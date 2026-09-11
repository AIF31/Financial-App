package com.aif31.pocket.notifications

internal data class ActiveNotificationIdentity(
    val sourcePackage: String,
    val notificationKey: String,
    val postedAtUtcMillis: Long,
)

internal class NotificationLifecycleIdentities {
    private data class Slot(val sourcePackage: String, val notificationKey: String)
    private data class Occurrence(val postedAtUtcMillis: Long, val sequence: Int)

    private val active = mutableMapOf<Slot, String>()
    private val lastOccurrence = mutableMapOf<Slot, Occurrence>()

    @Synchronized
    fun identityForPosted(
        sourcePackage: String,
        notificationKey: String,
        postedAtUtcMillis: Long,
    ): String {
        val slot = Slot(sourcePackage, notificationKey)
        return active.getOrPut(slot) {
            val previous = lastOccurrence[slot]
            val sequence = if (previous?.postedAtUtcMillis == postedAtUtcMillis) previous.sequence + 1 else 0
            lastOccurrence[slot] = Occurrence(postedAtUtcMillis, sequence)
            "$notificationKey\u0000$postedAtUtcMillis\u0000$sequence"
        }
    }

    @Synchronized
    fun onRemoved(sourcePackage: String, notificationKey: String) {
        active.remove(Slot(sourcePackage, notificationKey))
    }

    @Synchronized
    fun onListenerConnected(notifications: Collection<ActiveNotificationIdentity>) {
        val connectedSlots = notifications.mapTo(mutableSetOf()) {
            Slot(it.sourcePackage, it.notificationKey)
        }
        active.keys.retainAll(connectedSlots)
        notifications.forEach {
            identityForPosted(it.sourcePackage, it.notificationKey, it.postedAtUtcMillis)
        }
    }
}
