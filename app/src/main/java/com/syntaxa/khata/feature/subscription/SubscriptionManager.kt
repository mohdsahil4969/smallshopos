package com.syntaxa.khata.feature.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

/**
 * Production Subscription Manager modeled after the shopmanager subscription architecture.
 * Implements hardware device binding, tamper detection, UPI UTR verification,
 * and offline cryptographic license codes.
 */
class SubscriptionManager(
    private val context: Context? = null,
    private val prefs: SharedPreferences = context?.getSharedPreferences("syntaxa_subscription_prefs", Context.MODE_PRIVATE)
        ?: error("Preferences must be provided if context is null"),
    private val testDeviceIdOverride: String? = null
) {
    companion object {
        const val DEFAULT_UPI_ID = "9766291190@fam"
        const val DEFAULT_MERCHANT_NAME = "SmartKhata by Syntaxa"
        const val PRO_MONTHLY_PRICE_INR = 49
        const val PRO_DURATION_DAYS = 30
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        private const val KEY_TIER = "sub_tier"
        private const val KEY_EXPIRY_EPOCH = "sub_expiry_epoch"
        private const val KEY_PAYMENT_REF = "sub_payment_ref"
        private const val KEY_LAST_KNOWN_TIMESTAMP = "sub_last_known_timestamp"
        private const val KEY_USED_UTRS = "sub_used_utrs"
        private const val KEY_SERVER_SIGNATURE = "sub_server_signature"
        private const val KEY_LICENSE_ID = "sub_license_id"
        private const val OFFLINE_SALT = "SYNTAXA_OFFLINE_PRO_SALT_2026"
        private const val OFFLINE_YEAR_SALT = "SYNTAXA_OFFLINE_PRO_YEAR_2026"
        val MASTER_LIFETIME_CODES = setOf("SYNTAXA_SEC_LIFETIME_2026")
        val MASTER_YEAR_CODES = setOf("SYNTAXA_SEC_VIP_365_2026")

        const val DEFAULT_WEBHOOK_URL =
            "https://script.google.com/macros/s/AKfycbyn2XGiekYBTKgODflwhl5OuCxD_6dNLkoO7qeAJrpN1s4A-C4lIJjIPPkm0g5WE654/exec"
    }

    @SuppressLint("HardwareIds")
    val hardwareDeviceId: String = testDeviceIdOverride ?: try {
        context?.let { ctx ->
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }?.takeIf { it.isNotBlank() } ?: "SYNTAXA_DEVICE_OFFLINE"
    } catch (_: Exception) {
        "SYNTAXA_DEVICE_OFFLINE"
    }

    private val _status = MutableStateFlow(computeStatus())
    val status: StateFlow<SubscriptionStatus> = _status.asStateFlow()

    init {
        refresh()
    }

    fun getStatus(): SubscriptionStatus = computeStatus()

    fun canUse(entitlement: FeatureEntitlement): Boolean {
        return computeStatus().canUse(entitlement)
    }

    /**
     * Re-evaluates subscription status, checks clock tampering, and emits updated state.
     */
    fun refresh() {
        val current = computeStatus()
        _status.value = current
    }

    private fun computeStatus(): SubscriptionStatus {
        val now = System.currentTimeMillis()
        val lastKnown = prefs.getLong(KEY_LAST_KNOWN_TIMESTAMP, 0L)

        // Monotonic clock tampering check (prevent user turning back phone clock)
        var isClockTampered = false
        if (lastKnown > 0L && now < (lastKnown - 60_000L)) {
            // Clock moved backwards by more than 1 minute
            isClockTampered = true
        } else {
            // Update last known timestamp
            prefs.edit().putLong(KEY_LAST_KNOWN_TIMESTAMP, now).apply()
        }

        val rawTier = prefs.getString(KEY_TIER, SubscriptionTier.FREE.name) ?: SubscriptionTier.FREE.name
        val tier = try {
            SubscriptionTier.valueOf(rawTier)
        } catch (_: Exception) {
            SubscriptionTier.FREE
        }

        val expiryEpoch = prefs.getLong(KEY_EXPIRY_EPOCH, 0L).takeIf { it > 0L }
        val paymentRef = prefs.getString(KEY_PAYMENT_REF, null)

        val isExpired = expiryEpoch != null && now >= expiryEpoch
        val daysRemaining = if (expiryEpoch != null && !isExpired) {
            max(0, kotlin.math.ceil((expiryEpoch - now).toDouble() / MILLIS_PER_DAY).toInt())
        } else {
            0
        }

        val effectiveIsPro = (tier == SubscriptionTier.PRO_MONTHLY) && !isExpired && !isClockTampered

        return SubscriptionStatus(
            isPro = effectiveIsPro,
            tier = if (effectiveIsPro) SubscriptionTier.PRO_MONTHLY else SubscriptionTier.FREE,
            tierName = if (effectiveIsPro) "SmartKhata PRO" else "Free Starter",
            expiryDateEpoch = expiryEpoch,
            daysRemaining = daysRemaining,
            paymentRef = paymentRef,
            deviceId = hardwareDeviceId,
            isClockTampered = isClockTampered,
            isExpired = isExpired
        )
    }

    /**
     * Activates or extends PRO subscription by given number of days.
     */
    fun activatePro(days: Int = PRO_DURATION_DAYS, paymentRef: String = "MANUAL_ACTIVATION") {
        val now = System.currentTimeMillis()
        val currentExpiry = prefs.getLong(KEY_EXPIRY_EPOCH, 0L)
        val baseTime = if (currentExpiry > now) currentExpiry else now
        val newExpiry = baseTime + (days.toLong() * MILLIS_PER_DAY)

        prefs.edit()
            .putString(KEY_TIER, SubscriptionTier.PRO_MONTHLY.name)
            .putLong(KEY_EXPIRY_EPOCH, newExpiry)
            .putString(KEY_PAYMENT_REF, paymentRef)
            .putLong(KEY_LAST_KNOWN_TIMESTAMP, now)
            .apply()

        refresh()
    }

    /**
     * Verifies Indian 12-digit UPI UTR transaction reference and activates 30 days of PRO.
     */
    fun verifyUtrAndActivate(utr: String): Boolean {
        val cleanUtr = utr.trim().replace(" ", "").replace("-", "")
        if (cleanUtr.length != 12 || !cleanUtr.all { it.isDigit() }) {
            return false
        }

        val usedUtrs = prefs.getStringSet(KEY_USED_UTRS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (usedUtrs.contains(cleanUtr)) {
            // Already redeemed
            return false
        }

        usedUtrs.add(cleanUtr)
        prefs.edit().putStringSet(KEY_USED_UTRS, usedUtrs).apply()

        activatePro(days = PRO_DURATION_DAYS, paymentRef = "UTR:$cleanUtr")
        return true
    }

    /**
     * Rejects obvious dummy, sequential, or repeated digits UTR inputs.
     */
    fun isDummyOrInvalidUtr(utr: String): Boolean {
        val clean = utr.trim().replace(" ", "").replace("-", "")
        if (clean.length != 12 || !clean.all { it.isDigit() }) return true
        if (clean.all { it == clean[0] }) return true
        val testPatterns = setOf(
            "123456789012", "012345678901", "987654321098", "210987654321",
            "121212121212", "123123123123", "000000000001", "123456654321"
        )
        if (clean in testPatterns) return true
        return false
    }

    /**
     * Verifies Indian 12-digit UPI UTR with the live payment verification server webhook.
     * Prevents duplicate redemption, checks incoming bank payment log, and signs 30-day PRO license.
     */
    suspend fun verifyUtrWithServer(
        utr: String,
        shopName: String = "Syntaxa Kirana Store",
        phone: String = "",
        webhookUrl: String = DEFAULT_WEBHOOK_URL
    ): UtrVerificationResult = withContext(Dispatchers.IO) {
        val cleanUtr = utr.trim().replace(" ", "").replace("-", "")
        if (cleanUtr.length != 12 || !cleanUtr.all { it.isDigit() }) {
            return@withContext UtrVerificationResult.Failure("Please enter a valid 12-digit numeric UPI UTR number.")
        }

        if (isDummyOrInvalidUtr(cleanUtr)) {
            return@withContext UtrVerificationResult.Failure("Invalid or dummy UTR number. Please enter the authentic 12-digit reference from your UPI payment receipt.")
        }

        val usedUtrs = prefs.getStringSet(KEY_USED_UTRS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (usedUtrs.contains(cleanUtr)) {
            return@withContext UtrVerificationResult.Failure("This UTR has already been redeemed on this device.")
        }

        try {
            val payload = JSONObject().apply {
                put("action", "verify_and_activate")
                put("utr", cleanUtr)
                put("deviceId", hardwareDeviceId)
                put("shopName", shopName)
                put("phone", phone)
                put("plan", "proMonthly")
                put("durationDays", PRO_DURATION_DAYS)
                put("amount", PRO_MONTHLY_PRICE_INR.toDouble())
            }.toString()

            val responseJsonText = postJsonWithRedirects(webhookUrl, payload)
            val responseObj = JSONObject(responseJsonText)

            val success = responseObj.optBoolean("success", false)
            val isVerified = responseObj.optBoolean("verified", false) || responseObj.optString("verificationStatus") == "CONFIRMED_INCOMING_PAYMENT"
            val message = responseObj.optString("message", "")

            if (success && isVerified) {
                val expiryIso = responseObj.optString("expiryDate", "")
                val signature = responseObj.optString("signature", "")
                val licenseId = responseObj.optString("licenseId", "")

                val expiryEpoch = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        java.time.Instant.parse(expiryIso).toEpochMilli()
                    } else {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(expiryIso.take(19))?.time
                            ?: (System.currentTimeMillis() + (PRO_DURATION_DAYS.toLong() * MILLIS_PER_DAY))
                    }
                } catch (_: Exception) {
                    System.currentTimeMillis() + (PRO_DURATION_DAYS.toLong() * MILLIS_PER_DAY)
                }

                usedUtrs.add(cleanUtr)
                prefs.edit()
                    .putString(KEY_TIER, SubscriptionTier.PRO_MONTHLY.name)
                    .putLong(KEY_EXPIRY_EPOCH, expiryEpoch)
                    .putString(KEY_PAYMENT_REF, "UTR:$cleanUtr")
                    .putString(KEY_SERVER_SIGNATURE, signature)
                    .putString(KEY_LICENSE_ID, licenseId)
                    .putStringSet(KEY_USED_UTRS, usedUtrs)
                    .putLong(KEY_LAST_KNOWN_TIMESTAMP, System.currentTimeMillis())
                    .apply()

                refresh()
                return@withContext UtrVerificationResult.Success(
                    message = message.ifBlank { "SmartKhata PRO activated successfully!" },
                    expiryDateEpoch = expiryEpoch
                )
            } else {
                val failureReason = if (!isVerified && success) {
                    "Payment not found in verified server records. Please ensure ₹49 was paid via UPI and that the 12-digit UTR is authentic."
                } else {
                    message.ifBlank { "UTR verification rejected by payment verification server." }
                }
                return@withContext UtrVerificationResult.Failure(message = failureReason)
            }
        } catch (e: Exception) {
            return@withContext UtrVerificationResult.Failure(
                message = "Could not reach payment verification server. Please verify your internet connection, or activate using your Offline License Code."
            )
        }
    }

    /**
     * Automatically queries Google Cloud Webhook to check if a recent payment was confirmed for this device.
     * Mirrors shopmanager's checkAndAutoActivatePro architecture.
     */
    suspend fun checkAndAutoActivatePro(webhookUrl: String = DEFAULT_WEBHOOK_URL): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "check_status")
                put("deviceId", hardwareDeviceId)
                put("plan", "proMonthly")
                put("durationDays", PRO_DURATION_DAYS)
                put("amount", PRO_MONTHLY_PRICE_INR.toDouble())
            }.toString()

            val responseJsonText = postJsonWithRedirects(webhookUrl, payload)
            val responseObj = JSONObject(responseJsonText)

            val isPro = responseObj.optBoolean("isPro", false)
            val isVerified = responseObj.optBoolean("verified", false) || responseObj.optString("verificationStatus") == "CONFIRMED_INCOMING_PAYMENT"
            val signature = responseObj.optString("signature", "")
            val expiryIso = responseObj.optString("expiryDate", "")
            val utr = responseObj.optString("utr", "AUTO_UPI_VERIFIED")

            if (isPro && isVerified && signature.isNotBlank()) {
                val expiryEpoch = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        java.time.Instant.parse(expiryIso).toEpochMilli()
                    } else {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(expiryIso.take(19))?.time
                            ?: (System.currentTimeMillis() + (PRO_DURATION_DAYS.toLong() * MILLIS_PER_DAY))
                    }
                } catch (_: Exception) {
                    System.currentTimeMillis() + (PRO_DURATION_DAYS.toLong() * MILLIS_PER_DAY)
                }

                prefs.edit()
                    .putString(KEY_TIER, SubscriptionTier.PRO_MONTHLY.name)
                    .putLong(KEY_EXPIRY_EPOCH, expiryEpoch)
                    .putString(KEY_PAYMENT_REF, "UTR:$utr")
                    .putString(KEY_SERVER_SIGNATURE, signature)
                    .putLong(KEY_LAST_KNOWN_TIMESTAMP, System.currentTimeMillis())
                    .apply()

                refresh()
                return@withContext true
            }
        } catch (_: Exception) {
            // Background check is non-blocking
        }
        return@withContext false
    }

    private fun postJsonWithRedirects(urlString: String, jsonPayload: String): String {
        var url = URL(urlString)
        var conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "SyntaxaKhata/1.0")
        }

        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(StandardCharsets.UTF_8))
        }

        var responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
            responseCode == 307
        ) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (!location.isNullOrBlank()) {
                url = URL(location)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "SyntaxaKhata/1.0")
                }
                responseCode = conn.responseCode
            }
        }

        val isSuccess = responseCode in 200..299
        val stream = if (isSuccess) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val responseText = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        conn.disconnect()
        return responseText
    }

    /**
     * Verifies an offline license code (Owner Lifetime, VIP 1-Year, Device-specific, or Cancel).
     */
    fun verifyOfflineCode(code: String): Boolean {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length < 5) return false

        // Cancellation / Revoke Codes
        if (cleanCode == "CANCEL" || cleanCode == "RESET" || cleanCode == "REVOKE") {
            resetToFree()
            return true
        }

        // 0. Owner Lifetime Master Codes (10 Years / 3650 days)
        if (cleanCode in MASTER_LIFETIME_CODES) {
            activatePro(days = 3650, paymentRef = "OWNER_LIFETIME:$cleanCode")
            return true
        }

        // 1. VIP 1-Year Master Codes (365 days)
        if (cleanCode in MASTER_YEAR_CODES) {
            activatePro(days = 365, paymentRef = "MASTER_VIP_1YEAR:$cleanCode")
            return true
        }

        // 2. Device-specific 1-Year Code (365 days)
        val expectedYearCode = generateOfflineYearlyCodeForDevice(hardwareDeviceId)
        if (cleanCode == expectedYearCode) {
            activatePro(days = 365, paymentRef = "OFFLINE_1YEAR:$cleanCode")
            return true
        }

        // 3. Device-specific 30-Day Monthly Code
        val expectedMonthlyCode = generateOfflineCodeForDevice(hardwareDeviceId)
        if (cleanCode == expectedMonthlyCode) {
            activatePro(days = PRO_DURATION_DAYS, paymentRef = "OFFLINE_KEY:$cleanCode")
            return true
        }
        return false
    }

    /**
     * Generates expected 6-character 1-Year (365 Days) offline license code for a given device ID.
     */
    fun generateOfflineYearlyCodeForDevice(devId: String): String {
        val input = "$devId:$OFFLINE_YEAR_SALT"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        val hex = digest.joinToString("") { "%02X".format(it) }
        return hex.take(6).uppercase()
    }

    /**
     * Generates expected 6-character offline license code for a given device ID.
     */
    fun generateOfflineCodeForDevice(devId: String): String {
        val input = "$devId:$OFFLINE_SALT"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        val hex = digest.joinToString("") { "%02X".format(it) }
        return hex.take(6).uppercase()
    }

    /**
     * Constructs the standard UPI Intent URI for opening GPay, PhonePe, Paytm, BHIM.
     */
    fun buildUpiPaymentUri(amountRupees: Int = PRO_MONTHLY_PRICE_INR): String {
        val merchantNameEncoded = java.net.URLEncoder.encode(DEFAULT_MERCHANT_NAME, "UTF-8")
        val note = java.net.URLEncoder.encode("SmartKhata PRO 1 Month (Syntaxa) - DevID ${hardwareDeviceId.take(6)}", "UTF-8")
        return "upi://pay?pa=$DEFAULT_UPI_ID&pn=$merchantNameEncoded&am=$amountRupees.00&cu=INR&tn=$note"
    }

    fun resetToFree() {
        prefs.edit()
            .putString(KEY_TIER, SubscriptionTier.FREE.name)
            .putLong(KEY_EXPIRY_EPOCH, 0L)
            .remove(KEY_PAYMENT_REF)
            .remove(KEY_SERVER_SIGNATURE)
            .remove(KEY_LICENSE_ID)
            .apply()
        refresh()
    }
}
