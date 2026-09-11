package com.aif31.pocket.notifications

internal interface NotificationBetaMetrics {
    fun recordParserOutcome(parsed: Boolean)
    fun recordConfirmation(amountCorrected: Boolean, currencyCorrected: Boolean)
    fun snapshot(): NotificationBetaMetricsSnapshot
    fun reset()
}

internal data class NotificationBetaMetricsSnapshot(
    val parserAttempts: Int,
    val parserSuccesses: Int,
    val confirmations: Int,
    val correctedConfirmations: Int,
    val amountCorrections: Int,
    val currencyCorrections: Int,
) {
    val parserFailures: Int get() = parserAttempts - parserSuccesses
    val correctionRate: Double get() =
        if (confirmations == 0) 0.0 else correctedConfirmations.toDouble() / confirmations
}

internal object NoOpNotificationBetaMetrics : NotificationBetaMetrics {
    override fun recordParserOutcome(parsed: Boolean) = Unit
    override fun recordConfirmation(amountCorrected: Boolean, currencyCorrected: Boolean) = Unit
    override fun snapshot() = NotificationBetaMetricsSnapshot(0, 0, 0, 0, 0, 0)
    override fun reset() = Unit
}
