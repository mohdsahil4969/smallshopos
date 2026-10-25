package com.syntaxa.khata.core.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent preferences for the first-time user guide and onboarding walkthrough.
 */
class UserGuidePreferences(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("syntaxa_user_guide_prefs", Context.MODE_PRIVATE)
    )

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "has_completed_onboarding_walkthrough"
        private const val KEY_TERMS_ACCEPTED = "has_accepted_terms_and_conditions_v1"
    }

    /**
     * Checks if the user has completed or dismissed the first-run onboarding walkthrough.
     */
    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Sets whether the onboarding walkthrough has been completed.
     */
    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    /**
     * Checks if the user has accepted the Terms of Service & Software Disclaimer.
     */
    fun hasAcceptedTerms(): Boolean {
        return prefs.getBoolean(KEY_TERMS_ACCEPTED, false)
    }

    /**
     * Sets whether the terms of service and software disclaimer have been accepted.
     */
    fun setTermsAccepted(accepted: Boolean = true) {
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply()
    }

    /**
     * Resets terms acceptance (useful for testing or legal re-acceptance).
     */
    fun resetTermsAccepted() {
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, false).apply()
    }

    /**
     * Resets the onboarding status so the user can replay the full walkthrough from Settings.
     */
    fun resetOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, false).apply()
    }
}
