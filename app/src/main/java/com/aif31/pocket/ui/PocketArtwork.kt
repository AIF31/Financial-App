package com.aif31.pocket.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.aif31.pocket.R
import com.aif31.pocket.data.PocketIconKey

internal data class PocketIconOption(
    val key: PocketIconKey,
    val label: String,
)

internal val pocketIconOptions = listOf(
    PocketIconOption(PocketIconKey.SUPERMARKET, "Supermercado"),
    PocketIconOption(PocketIconKey.RESTAURANT, "Restaurantes y café"),
    PocketIconOption(PocketIconKey.TRANSPORT, "Transporte"),
    PocketIconOption(PocketIconKey.UNIVERSITY, "Universidad"),
    PocketIconOption(PocketIconKey.HEALTH, "Salud"),
    PocketIconOption(PocketIconKey.TRAVEL, "Viajes"),
    PocketIconOption(PocketIconKey.LEISURE, "Ocio"),
    PocketIconOption(PocketIconKey.GIFTS, "Regalos"),
    PocketIconOption(PocketIconKey.EMERGENCY, "Emergencia"),
)

@DrawableRes
private fun PocketIconKey.drawableResource(): Int? = when (this) {
    PocketIconKey.SUPERMARKET -> R.drawable.pocket_icon_supermarket
    PocketIconKey.RESTAURANT -> R.drawable.pocket_icon_restaurant
    PocketIconKey.TRANSPORT -> R.drawable.pocket_icon_transport
    PocketIconKey.UNIVERSITY -> R.drawable.pocket_icon_university
    PocketIconKey.HEALTH -> R.drawable.pocket_icon_health
    PocketIconKey.TRAVEL -> R.drawable.pocket_icon_travel
    PocketIconKey.LEISURE -> R.drawable.pocket_icon_leisure
    PocketIconKey.GIFTS -> R.drawable.pocket_icon_gifts
    PocketIconKey.EMERGENCY -> R.drawable.pocket_icon_emergency
    PocketIconKey.OTHER -> null
}

@Composable
internal fun PocketArtwork(
    iconKey: PocketIconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val resource = iconKey.drawableResource()
    if (resource != null) {
        Image(
            painter = painterResource(resource),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}