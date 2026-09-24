package com.aif31.pocket.settings

sealed interface ReminderStatus {
    data object Off : ReminderStatus
    data object PermissionRequired : ReminderStatus
    data object Scheduled : ReminderStatus
    data object Failed : ReminderStatus
}

internal fun resolveReminderStatus(
    enabled: Boolean,
    permissionGranted: Boolean,
    channelAvailable: Boolean,
    workScheduled: Boolean,
): ReminderStatus = when {
    !enabled -> ReminderStatus.Off
    !permissionGranted -> ReminderStatus.PermissionRequired
    !channelAvailable || !workScheduled -> ReminderStatus.Failed
    else -> ReminderStatus.Scheduled
}
