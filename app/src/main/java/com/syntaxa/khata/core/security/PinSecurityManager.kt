package com.syntaxa.khata.core.security

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinVerifyResult {
    object Success : PinVerifyResult()
    data class Incorrect(val attemptsLeftBeforeLockout: Int) : PinVerifyResult()
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult()
}

class PinSecurityManager(
    private val prefs: SharedPreferences
) {
    // Secondary constructor for Android Context callers
    constructor(context: Context) : this(
        context.getSharedPreferences("syntaxa_security_prefs", Context.MODE_PRIVATE)
    )

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_VERSION = "pin_version"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val HASH_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16

        // Max consecutive attempts before tier-1 lockout
        const val MAX_ATTEMPTS_BEFORE_SHORT_LOCKOUT = 5
    }

    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_ENABLED, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Sets a new 4-digit PIN using PBKDF2WithHmacSHA256 with 100,000 iterations and a 16-byte random salt.
     * Clears sensitive character arrays from memory immediately after key derivation.
     */
    fun setPin(pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be exactly 4 digits" }

        val saltBytes = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = bytesToHex(saltBytes)

        val hashHex = hashPinPbkdf2(pin, saltBytes)

        prefs.edit()
            .putString(KEY_PIN_HASH, hashHex)
            .putString(KEY_PIN_SALT, saltHex)
            .putInt(KEY_PIN_VERSION, 2)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun disablePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_VERSION)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    /**
     * Returns remaining lockout seconds if currently locked out, or 0 if allowed to attempt.
     */
    fun getLockoutRemainingSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            (lockoutUntil - now + 999) / 1000
        } else {
            0L
        }
    }

    /**
     * Verifies the PIN with persistent attempt throttling and progressive lockout delays.
     */
    fun verifyPinWithThrottling(pin: String): PinVerifyResult {
        val remainingLockout = getLockoutRemainingSeconds()
        if (remainingLockout > 0) {
            return PinVerifyResult.LockedOut(remainingLockout)
        }

        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return PinVerifyResult.Incorrect(0)
        val storedSaltHex = prefs.getString(KEY_PIN_SALT, null) ?: return PinVerifyResult.Incorrect(0)
        val version = prefs.getInt(KEY_PIN_VERSION, 1)

        val isValid = if (version >= 2) {
            val saltBytes = hexToBytes(storedSaltHex)
            val testHash = hashPinPbkdf2(pin, saltBytes)
            testHash == storedHash
        } else {
            // Legacy v1 migration check
            val legacyHash = hashPinSha256Legacy(pin, storedSaltHex)
            if (legacyHash == storedHash) {
                // Seamlessly upgrade to PBKDF2 v2
                setPin(pin)
                true
            } else {
                false
            }
        }

        return if (isValid) {
            // Reset attempts on success
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            PinVerifyResult.Success
        } else {
            val currentFailed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val lockoutDurationMillis = when {
                currentFailed >= 10 -> 15 * 60 * 1000L // 15 minutes
                currentFailed >= 8 -> 2 * 60 * 1000L  // 2 minutes
                currentFailed >= 5 -> 30 * 1000L      // 30 seconds
                else -> 0L
            }

            val lockoutUntil = if (lockoutDurationMillis > 0) {
                System.currentTimeMillis() + lockoutDurationMillis
            } else {
                0L
            }

            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, currentFailed)
                .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                .apply()

            if (lockoutDurationMillis > 0) {
                PinVerifyResult.LockedOut(lockoutDurationMillis / 1000)
            } else {
                val attemptsLeft = maxOf(0, MAX_ATTEMPTS_BEFORE_SHORT_LOCKOUT - currentFailed)
                PinVerifyResult.Incorrect(attemptsLeft)
            }
        }
    }

    /**
     * Backward-compatible simple boolean verification for internal callers.
     */
    fun verifyPin(pin: String): Boolean {
        return verifyPinWithThrottling(pin) is PinVerifyResult.Success
    }

    private fun hashPinPbkdf2(pin: String, salt: ByteArray): String {
        val pinChars = pin.toCharArray()
        try {
            val keySpec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val hashBytes = factory.generateSecret(keySpec).encoded
            keySpec.clearPassword()
            return bytesToHex(hashBytes)
        } finally {
            pinChars.fill('\u0000')
        }
    }

    private fun hashPinSha256Legacy(pin: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$pin"
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(bytes)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
