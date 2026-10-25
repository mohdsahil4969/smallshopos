package com.syntaxa.khata.feature.subscription

/**
 * Subscription Tiers for Syntaxa Khata.
 * Free tier offers unlimited core khata & billing.
 * Pro tier (₹49/month) unlocks advanced hardware, inventory, supplier, and financial tools.
 */
enum class SubscriptionTier {
    FREE,
    PRO_MONTHLY;

    val title: String
        get() = when (this) {
            FREE -> "Free Starter"
            PRO_MONTHLY -> "Syntaxa PRO"
        }

    val priceRupees: Int
        get() = when (this) {
            FREE -> 0
            PRO_MONTHLY -> 49
        }

    val formattedPrice: String
        get() = when (this) {
            FREE -> "₹0 / Free"
            PRO_MONTHLY -> "₹49 / month"
        }

    val isPro: Boolean
        get() = this == PRO_MONTHLY
}

/**
 * Semantic feature entitlements matching shopmanager subscription gating architecture.
 */
enum class FeatureEntitlement {
    // Core Free entitlements (Always active offline)
    BASIC_BILLING,
    QUICK_BILLING,
    CUSTOMERS_KHATA,
    UDHAAR_TRACKING,
    BASIC_EXPENSES,
    BASIC_REPORTS,
    OFFLINE_USAGE,
    LOCAL_BACKUP,

    // Pro Entitlements (Locked behind ₹49/month PRO subscription)
    PDF_THERMAL_PRINTING,       // Bluetooth 58mm/80mm ESC/POS thermal printing
    FULL_INVENTORY,             // Product catalog & automatic stock deduction
    ADVANCED_STOCK_ALERTS,      // Low stock badges & reorder thresholds
    SUPPLIERS_PURCHASES,        // Supplier/Wholesaler purchases & payable dues ledger
    DAILY_CASH_CLOSING,         // End-of-day drawer cash reconciler & formula
    CUSTOM_BRANDING,            // Custom shop watermark & removing "Powered by Syntaxa"
    ADVANCED_REPORTS            // Detailed profit/loss & custom date range exports
}

data class SubscriptionStatus(
    val isPro: Boolean,
    val tier: SubscriptionTier,
    val tierName: String,
    val expiryDateEpoch: Long?,
    val daysRemaining: Int,
    val paymentRef: String?,
    val deviceId: String,
    val isClockTampered: Boolean = false,
    val isExpired: Boolean = false
) {
    /**
     * Checks if this status permits using the requested feature entitlement.
     */
    fun canUse(entitlement: FeatureEntitlement): Boolean {
        if (isClockTampered) return isFreeEntitlement(entitlement)
        if (!isPro || isExpired) {
            return isFreeEntitlement(entitlement)
        }
        return true
    }

    companion object {
        fun isFreeEntitlement(entitlement: FeatureEntitlement): Boolean {
            return when (entitlement) {
                FeatureEntitlement.BASIC_BILLING,
                FeatureEntitlement.QUICK_BILLING,
                FeatureEntitlement.CUSTOMERS_KHATA,
                FeatureEntitlement.UDHAAR_TRACKING,
                FeatureEntitlement.BASIC_EXPENSES,
                FeatureEntitlement.BASIC_REPORTS,
                FeatureEntitlement.OFFLINE_USAGE,
                FeatureEntitlement.LOCAL_BACKUP -> true
                else -> false
            }
        }
    }
}

sealed class UtrVerificationResult {
    data class Success(val message: String, val expiryDateEpoch: Long? = null) : UtrVerificationResult()
    data class Failure(val message: String) : UtrVerificationResult()
}
