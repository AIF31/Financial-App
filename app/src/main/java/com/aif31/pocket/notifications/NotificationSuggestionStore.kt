package com.aif31.pocket.notifications

import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.MovementSuggestionEntity
import androidx.room.withTransaction
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.CancellationException

internal class NotificationSuggestionStore(
    private val database: FinanceDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun ingest(sourcePackage: String, notificationIdentity: String, postedAtUtcMillis: Long, payment: ParsedPayment) {
        val dao = database.financeDao()
        val identityHash = hash("$sourcePackage\u0000$notificationIdentity")
        val nowUtcMillis = clock.millis()
        database.withTransaction {
            dao.deleteExpiredMovementSuggestions(nowUtcMillis)
            val existing = dao.movementSuggestion(identityHash)
            if (existing != null && existing.status != "PENDING") return@withTransaction
            if (existing?.effectiveAtUtcMillis?.let { it > postedAtUtcMillis } == true) return@withTransaction
            dao.putMovementSuggestion(MovementSuggestionEntity(
                identityHash = identityHash,
                amountMinor = payment.amountMinor,
                currencyCode = payment.currency.name,
                effectiveAtUtcMillis = postedAtUtcMillis,
                sourcePackage = sourcePackage,
                merchant = payment.merchant,
                status = "PENDING",
                expiresAtUtcMillis = existing?.expiresAtUtcMillis ?: Math.addExact(nowUtcMillis, THIRTY_DAYS_MILLIS),
            ))
        }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object { const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000 }
}

internal class NotificationCapture(
    private val store: NotificationSuggestionStore,
    private val metrics: NotificationBetaMetrics = NoOpNotificationBetaMetrics,
) {
    suspend fun ingest(
        allowedPackages: Set<String>,
        sourcePackage: String,
        notificationIdentity: String,
        postedAtUtcMillis: Long,
        title: CharSequence?,
        text: CharSequence?,
    ): Boolean {
        if (sourcePackage !in allowedPackages) return false
        val payment = runCatching { NotificationPaymentParser.parse(title, text) }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
        runCatching { metrics.recordParserOutcome(payment != null) }
        payment ?: return false
        store.ingest(sourcePackage, notificationIdentity, postedAtUtcMillis, payment)
        return true
    }
}
