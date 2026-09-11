package com.aif31.pocket.notifications

import android.content.Context
import android.content.SharedPreferences

internal fun notificationBetaMetrics(context: Context): NotificationBetaMetrics =
    SharedPreferencesNotificationBetaMetrics(
        context.getSharedPreferences("notification_beta_metrics", Context.MODE_PRIVATE),
    )

internal class SharedPreferencesNotificationBetaMetrics(
    private val preferences: SharedPreferences,
) : NotificationBetaMetrics {
    @Synchronized
    override fun recordParserOutcome(parsed: Boolean) {
        preferences.edit()
            .putInt(PARSER_ATTEMPTS, preferences.getInt(PARSER_ATTEMPTS, 0) + 1)
            .apply {
                if (parsed) putInt(PARSER_SUCCESSES, preferences.getInt(PARSER_SUCCESSES, 0) + 1)
            }
            .apply()
    }

    @Synchronized
    override fun recordConfirmation(amountCorrected: Boolean, currencyCorrected: Boolean) {
        preferences.edit()
            .putInt(CONFIRMATIONS, preferences.getInt(CONFIRMATIONS, 0) + 1)
            .apply {
                if (amountCorrected || currencyCorrected) {
                    putInt(CORRECTED_CONFIRMATIONS, preferences.getInt(CORRECTED_CONFIRMATIONS, 0) + 1)
                }
                if (amountCorrected) putInt(AMOUNT_CORRECTIONS, preferences.getInt(AMOUNT_CORRECTIONS, 0) + 1)
                if (currencyCorrected) putInt(CURRENCY_CORRECTIONS, preferences.getInt(CURRENCY_CORRECTIONS, 0) + 1)
            }
            .apply()
    }

    @Synchronized
    override fun snapshot() = NotificationBetaMetricsSnapshot(
        parserAttempts = preferences.getInt(PARSER_ATTEMPTS, 0),
        parserSuccesses = preferences.getInt(PARSER_SUCCESSES, 0),
        confirmations = preferences.getInt(CONFIRMATIONS, 0),
        correctedConfirmations = preferences.getInt(CORRECTED_CONFIRMATIONS, 0),
        amountCorrections = preferences.getInt(AMOUNT_CORRECTIONS, 0),
        currencyCorrections = preferences.getInt(CURRENCY_CORRECTIONS, 0),
    )

    @Synchronized
    override fun reset() = preferences.edit().clear().commit().let { Unit }

    private companion object {
        const val PARSER_ATTEMPTS = "parser_attempts"
        const val PARSER_SUCCESSES = "parser_successes"
        const val CONFIRMATIONS = "confirmations"
        const val CORRECTED_CONFIRMATIONS = "corrected_confirmations"
        const val AMOUNT_CORRECTIONS = "amount_corrections"
        const val CURRENCY_CORRECTIONS = "currency_corrections"
    }
}
