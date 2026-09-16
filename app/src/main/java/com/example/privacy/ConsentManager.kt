package com.example.privacy

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Manages Google User Messaging Platform (UMP) SDK consent flows
 * in compliance with Google Play Developer Policies and GDPR / CCPA.
 */
class ConsentManager(private val context: Context) {

    companion object {
        private const val TAG = "ConsentManager"
    }

    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    /**
     * Requests consent information update and presents the consent form if required.
     * Invokes [onConsentCompleted] once the form is dismissed or if consent is not required.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentCompleted: (canRequestAds: Boolean) -> Unit
    ) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.errorCode} - ${formError.message}")
                    }
                    onConsentCompleted(consentInformation.canRequestAds())
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent update error: ${requestConsentError.errorCode} - ${requestConsentError.message}")
                onConsentCompleted(consentInformation.canRequestAds())
            }
        )
    }

    /**
     * Checks if ads are permitted to be requested under the current consent status.
     */
    fun canRequestAds(): Boolean {
        return consentInformation.canRequestAds()
    }

    /**
     * Checks if user has an option to review/change their consent choices.
     */
    fun isPrivacyOptionsRequired(): Boolean {
        return consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Presents the privacy options form so users can update their consent choices at any time.
     */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
            }
            onDismissed()
        }
    }
}
