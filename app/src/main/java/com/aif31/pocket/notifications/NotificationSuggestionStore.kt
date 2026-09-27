package com.aif31.pocket.notifications

import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.MovementSuggestionEntity
import com.aif31.pocket.domain.SupportedCurrency
import androidx.room.withTransaction
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.CancellationException

internal enum class IngestOutcome { CREATED, UPDATED, IGNORED }

internal data class IngestResult(val suggestionId: String, val outcome: IngestOutcome)

internal class NotificationSuggestionStore(
    private val database: FinanceDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun ingest(
        sourcePackage: String,
        notificationIdentity: String,
        postedAtUtcMillis: Long,
        payment: ParsedPayment,
    ): IngestResult {
        val dao = database.financeDao()
        val identityHash = identityHash(sourcePackage, notificationIdentity)
        val nowUtcMillis = clock.millis()
        val outcome = database.withTransaction {
            dao.deleteExpiredMovementSuggestions(nowUtcMillis)
            val existing = dao.movementSuggestion(identityHash)
            if (existing != null && existing.status != "PENDING") return@withTransaction IngestOutcome.IGNORED
            if (existing?.effectiveAtUtcMillis?.let { it > postedAtUtcMillis } == true) return@withTransaction IngestOutcome.IGNORED
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
            if (existing == null) IngestOutcome.CREATED else IngestOutcome.UPDATED
        }
        return IngestResult(identityHash, outcome)
    }

    /** True once a message was stored, including confirmed or discarded tombstones that have not expired. */
    suspend fun contains(sourcePackage: String, notificationIdentity: String): Boolean =
        database.financeDao().movementSuggestion(identityHash(sourcePackage, notificationIdentity))
            ?.let { it.expiresAtUtcMillis > clock.millis() } == true

    private fun identityHash(sourcePackage: String, notificationIdentity: String) =
        hash("$sourcePackage\u0000$notificationIdentity")

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object { const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000 }
}

/** A payment captured from one notification message, still held only as a pending suggestion. */
internal data class CapturedPayment(
    val suggestionId: String,
    val outcome: IngestOutcome,
    val payment: ParsedPayment,
    val sourcePackage: String,
    val effectiveAtUtcMillis: Long,
)

/**
 * One message read from a notification. Messaging apps append every new message to one conversation
 * notification, so identity comes from the conversation, the message time, and its text, never the slot.
 */
internal data class NotificationMessage(
    val conversation: String,
    val postedAtUtcMillis: Long,
    val title: CharSequence?,
    val text: CharSequence?,
    /** A chat message's own send time. Null for a plain notification, whose updates change the post time. */
    val sentAtUtcMillis: Long? = postedAtUtcMillis,
)

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
        dollarCurrency: SupportedCurrency? = null,
    ): CapturedPayment? {
        if (sourcePackage !in allowedPackages) return null
        val payment = runCatching { NotificationPaymentParser.parse(title, text, dollarCurrency) }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
        runCatching { metrics.recordParserOutcome(payment != null) }
        payment ?: return null
        val result = store.ingest(sourcePackage, notificationIdentity, postedAtUtcMillis, payment)
        return CapturedPayment(result.suggestionId, result.outcome, payment, sourcePackage, postedAtUtcMillis)
    }

    /** Parses each message on its own, so an OTP beside a purchase cannot hide it. */
    suspend fun ingestMessages(
        allowedPackages: Set<String>,
        sourcePackage: String,
        messages: List<NotificationMessage>,
        dollarCurrency: SupportedCurrency? = null,
    ): List<CapturedPayment> {
        if (sourcePackage !in allowedPackages) return emptyList()
        return messages.mapNotNull { message ->
            val identity = "message\u0000${message.conversation}\u0000${message.sentAtUtcMillis ?: ""}\u0000${message.text}"
            // Conversations are re-posted with every earlier message; parse each one only once.
            if (store.contains(sourcePackage, identity)) return@mapNotNull null
            ingest(
                allowedPackages = allowedPackages,
                sourcePackage = sourcePackage,
                notificationIdentity = identity,
                postedAtUtcMillis = message.postedAtUtcMillis,
                title = message.title,
                text = message.text,
                dollarCurrency = dollarCurrency,
            )
        }
    }
}
