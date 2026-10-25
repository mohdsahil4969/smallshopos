package com.syntaxa.khata

import android.content.SharedPreferences
import com.syntaxa.khata.feature.subscription.FeatureEntitlement
import com.syntaxa.khata.feature.subscription.SubscriptionManager
import com.syntaxa.khata.feature.subscription.SubscriptionTier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Security, Tamper-Resistance & Entitlement Verification Test Suite for Syntaxa Khata PRO.
 * Mirrors the exact subscription test architecture of the shopmanager reference implementation:
 * 1. Default initial state is FREE tier with full offline core Kirana billing.
 * 2. PRO activation (₹49/month) unlocks thermal printing, inventory deduction, and wholesaler ledger.
 * 3. 12-digit Indian UPI UTR verification & replay attack defense.
 * 4. Monotonic clock rollback anti-tampering defenses.
 * 5. Device-bound SHA-256 offline license activation.
 * 6. Subscription duration stacking and graceful expiration.
 */
class SubscriptionSecurityTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var subscriptionManager: SubscriptionManager
    private val testDeviceId = "SYNTAXA_TEST_DEVICE_A1B2C3"

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        subscriptionManager = SubscriptionManager(
            context = null,
            prefs = fakePrefs,
            testDeviceIdOverride = testDeviceId
        )
    }

    @Test
    fun test0_defaultInitialState_isFreeTierWithCoreBillingAllowed() {
        val status = subscriptionManager.getStatus()

        assertFalse("New install must default to Free tier", status.isPro)
        assertEquals(SubscriptionTier.FREE, status.tier)
        assertEquals("Free Starter", status.tierName)
        assertEquals(0, status.daysRemaining)
        assertFalse(status.isClockTampered)
        assertFalse(status.isExpired)

        // Core Kirana Free entitlements must be unlocked
        assertTrue(status.canUse(FeatureEntitlement.BASIC_BILLING))
        assertTrue(status.canUse(FeatureEntitlement.QUICK_BILLING))
        assertTrue(status.canUse(FeatureEntitlement.CUSTOMERS_KHATA))
        assertTrue(status.canUse(FeatureEntitlement.UDHAAR_TRACKING))
        assertTrue(status.canUse(FeatureEntitlement.BASIC_EXPENSES))
        assertTrue(status.canUse(FeatureEntitlement.BASIC_REPORTS))
        assertTrue(status.canUse(FeatureEntitlement.OFFLINE_USAGE))
        assertTrue(status.canUse(FeatureEntitlement.LOCAL_BACKUP))

        // Premium PRO features must be strictly gated
        assertFalse("Bluetooth thermal printing must be PRO", status.canUse(FeatureEntitlement.PDF_THERMAL_PRINTING))
        assertFalse("Stock tracking catalog must be PRO", status.canUse(FeatureEntitlement.FULL_INVENTORY))
        assertFalse("Supplier khata must be PRO", status.canUse(FeatureEntitlement.SUPPLIERS_PURCHASES))
        assertFalse("Daily cash closing reconciler must be PRO", status.canUse(FeatureEntitlement.DAILY_CASH_CLOSING))
        assertFalse("Custom branding watermark removal must be PRO", status.canUse(FeatureEntitlement.CUSTOM_BRANDING))
    }

    @Test
    fun test1_activatePro_grantsProTierAndAllEntitlementsFor30Days() {
        subscriptionManager.activatePro(days = 30, paymentRef = "TEST_PAYMENT_123")
        val status = subscriptionManager.getStatus()

        assertTrue("PRO must be active", status.isPro)
        assertEquals(SubscriptionTier.PRO_MONTHLY, status.tier)
        assertEquals("Syntaxa PRO", status.tierName)
        assertTrue("Days remaining should be between 29 and 30", status.daysRemaining in 29..30)
        assertEquals("TEST_PAYMENT_123", status.paymentRef)

        // All entitlements must now be active
        assertTrue(status.canUse(FeatureEntitlement.BASIC_BILLING))
        assertTrue(status.canUse(FeatureEntitlement.PDF_THERMAL_PRINTING))
        assertTrue(status.canUse(FeatureEntitlement.FULL_INVENTORY))
        assertTrue(status.canUse(FeatureEntitlement.SUPPLIERS_PURCHASES))
        assertTrue(status.canUse(FeatureEntitlement.DAILY_CASH_CLOSING))
        assertTrue(status.canUse(FeatureEntitlement.CUSTOM_BRANDING))
    }

    @Test
    fun test2_utrVerification_valid12DigitUtrActivatesPro() {
        val validUtr = "987654321012"
        val success = subscriptionManager.verifyUtrAndActivate(validUtr)

        assertTrue("Valid 12-digit UTR must successfully activate PRO", success)
        val status = subscriptionManager.getStatus()
        assertTrue(status.isPro)
        assertEquals("UTR:$validUtr", status.paymentRef)
        assertTrue(status.daysRemaining >= 29)
    }

    @Test
    fun test3_utrVerification_replayAttackPrevention() {
        val utr = "112233445566"
        val firstAttempt = subscriptionManager.verifyUtrAndActivate(utr)
        assertTrue("First attempt should succeed", firstAttempt)

        val secondAttempt = subscriptionManager.verifyUtrAndActivate(utr)
        assertFalse("Replaying identical UTR must be rejected", secondAttempt)

        val whitespaceAttempt = subscriptionManager.verifyUtrAndActivate(" 1122-3344 5566 ")
        assertFalse("Replaying formatted identical UTR must be rejected", whitespaceAttempt)
    }

    @Test
    fun test4_utrVerification_invalidFormatRejected() {
        assertFalse("Short UTR (<12 digits) must fail", subscriptionManager.verifyUtrAndActivate("12345678901"))
        assertFalse("Long UTR (>12 digits) must fail", subscriptionManager.verifyUtrAndActivate("1234567890123"))
        assertFalse("Alphanumeric UTR must fail", subscriptionManager.verifyUtrAndActivate("12345678901A"))
        assertFalse("Empty UTR must fail", subscriptionManager.verifyUtrAndActivate(""))
        assertFalse("Whitespace-only UTR must fail", subscriptionManager.verifyUtrAndActivate("            "))

        // Status must remain FREE
        assertFalse(subscriptionManager.getStatus().isPro)
    }

    @Test
    fun test5_clockRollbackDefense_revokesProEntitlementsUponClockManipulation() {
        subscriptionManager.activatePro(days = 30, paymentRef = "TAMPER_TEST_UTR")
        assertTrue(subscriptionManager.getStatus().isPro)

        // Attacker sets a future timestamp in preferences and rolls back local clock by 2 days
        val now = System.currentTimeMillis()
        val futureWatermark = now + (2L * 24L * 60L * 60L * 1000L)
        fakePrefs.edit().putLong("sub_last_known_timestamp", futureWatermark).apply()

        // Re-evaluating status detects clock moved backwards
        val status = subscriptionManager.getStatus()

        assertTrue("Clock rollback must be flagged as tampered", status.isClockTampered)
        assertFalse("PRO privileges must be suspended when clock is tampered", status.isPro)

        // Premium features must be denied during clock tampering
        assertFalse(status.canUse(FeatureEntitlement.PDF_THERMAL_PRINTING))
        assertFalse(status.canUse(FeatureEntitlement.FULL_INVENTORY))
        assertFalse(status.canUse(FeatureEntitlement.DAILY_CASH_CLOSING))

        // Core billing must still be available (never lockout merchant's core business)
        assertTrue(status.canUse(FeatureEntitlement.BASIC_BILLING))
        assertTrue(status.canUse(FeatureEntitlement.UDHAAR_TRACKING))
    }

    @Test
    fun test6_offlineCryptographicCode_validMatchesDeviceIdAndActivatesPro() {
        val validCode = subscriptionManager.generateOfflineCodeForDevice(testDeviceId)
        assertEquals("Offline license code must be 6 hex characters", 6, validCode.length)

        val success = subscriptionManager.verifyOfflineCode(validCode)
        assertTrue("Valid offline cryptographic key must activate PRO", success)

        val status = subscriptionManager.getStatus()
        assertTrue(status.isPro)
        assertEquals("OFFLINE_KEY:$validCode", status.paymentRef)
        assertTrue(status.daysRemaining >= 29)
    }

    @Test
    fun test7_offlineCryptographicCode_foreignDeviceCodeRejected() {
        val foreignCode = subscriptionManager.generateOfflineCodeForDevice("OTHER_DEVICE_999999")
        val success = subscriptionManager.verifyOfflineCode(foreignCode)

        assertFalse("Cryptographic key meant for another device must be rejected", success)
        assertFalse(subscriptionManager.getStatus().isPro)

        assertFalse("Random 6-char string must fail", subscriptionManager.verifyOfflineCode("ABC123"))
        assertFalse("Short string must fail", subscriptionManager.verifyOfflineCode("ABC"))
    }

    @Test
    fun test8_expiration_revertsToFreeTierAfterDuration() {
        val now = System.currentTimeMillis()
        val pastExpiry = now - 1000L // Expired 1 second ago

        fakePrefs.edit()
            .putString("sub_tier", SubscriptionTier.PRO_MONTHLY.name)
            .putLong("sub_expiry_epoch", pastExpiry)
            .putLong("sub_last_known_timestamp", now)
            .apply()

        val status = subscriptionManager.getStatus()

        assertTrue("Must be flagged as expired", status.isExpired)
        assertFalse("Expired PRO must not have PRO active", status.isPro)
        assertEquals(SubscriptionTier.FREE, status.tier)
        assertEquals(0, status.daysRemaining)

        // Free features still work
        assertTrue(status.canUse(FeatureEntitlement.BASIC_BILLING))
        // Pro features locked
        assertFalse(status.canUse(FeatureEntitlement.FULL_INVENTORY))
    }

    @Test
    fun test9_upiPaymentUri_containsCorrectMerchantAndAmount() {
        val uri = subscriptionManager.buildUpiPaymentUri(49)

        assertTrue("URI must use upi scheme", uri.startsWith("upi://pay?"))
        assertTrue("URI must contain merchant UPI ID", uri.contains("pa=9766291190%40fam") || uri.contains("pa=9766291190@fam"))
        assertTrue("URI must specify ₹49.00 amount", uri.contains("am=49.00"))
        assertTrue("URI must specify INR currency", uri.contains("cu=INR"))
        assertTrue("URI must mention Syntaxa", uri.contains("Syntaxa"))
    }

    @Test
    fun test10_proExtension_stacksDaysOnExistingSubscription() {
        subscriptionManager.activatePro(days = 30, paymentRef = "INITIAL_30")
        val initialDays = subscriptionManager.getStatus().daysRemaining

        subscriptionManager.activatePro(days = 30, paymentRef = "EXTENSION_30")
        val extendedDays = subscriptionManager.getStatus().daysRemaining

        assertTrue(
            "Extending subscription must stack days (expected ~59-60, got $extendedDays)",
            extendedDays >= initialDays + 29
        )
    }

    @Test
    fun test11_resetToFree_clearsProState() {
        subscriptionManager.activatePro(days = 30, paymentRef = "RESET_TEST")
        assertTrue(subscriptionManager.getStatus().isPro)

        subscriptionManager.resetToFree()
        val status = subscriptionManager.getStatus()

        assertFalse(status.isPro)
        assertEquals(SubscriptionTier.FREE, status.tier)
        assertEquals(0, status.daysRemaining)
        assertNull(status.paymentRef)
    }

    @Test
    fun test12_serverUtrVerification_invalidFormatFailsImmediately() = kotlinx.coroutines.runBlocking {
        val result = subscriptionManager.verifyUtrWithServer("12345")
        assertTrue("Short UTR must return failure", result is com.syntaxa.khata.feature.subscription.UtrVerificationResult.Failure)
        assertFalse(subscriptionManager.getStatus().isPro)
    }

    // --- In-Memory SharedPreferences implementation for unit test hermeticity ---
    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<*>)?.map { it.toString() }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val backingMap: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private val removeSet = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                tempMap[key] = values
                removeSet.remove(key)
                return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                removeSet.remove(key)
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                removeSet.add(key)
                tempMap.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) {
                    backingMap.clear()
                }
                for (key in removeSet) {
                    backingMap.remove(key)
                }
                for ((key, value) in tempMap) {
                    if (value == null) {
                        backingMap.remove(key)
                    } else {
                        backingMap[key] = value
                    }
                }
                tempMap.clear()
                removeSet.clear()
                clearAll = false
            }
        }
    }
}
