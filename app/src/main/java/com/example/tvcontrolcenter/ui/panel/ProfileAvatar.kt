package com.example.tvcontrolcenter.ui.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.data.ProfilePhoto
import com.example.tvcontrolcenter.domain.model.PanelSettings
import com.example.tvcontrolcenter.ui.theme.PanelDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colours offered for the profile circle when there is no photo. */
internal val AvatarColors = listOf(
    Color(0xFF5E7CE2), Color(0xFFE2655E), Color(0xFF4FB286),
    Color(0xFFD9A441), Color(0xFF9B6BD6), Color(0xFF6C7A89),
)

/** The profile button: the saved photo, or a coloured circle with a person. */
@Composable
internal fun ProfileFace(settings: PanelSettings) {
    val context = LocalContext.current.applicationContext
    val version = settings.profilePhotoVersion
    val photo by produceState<ImageBitmap?>(null, version) {
        value = if (version == 0) null else withContext(Dispatchers.IO) { ProfilePhoto.load(context) }
    }
    Box(Modifier.fillMaxSize().padding(3.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        val image = photo
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.fillMaxSize().background(AvatarColors[settings.profileColor.mod(AvatarColors.size)]),
                contentAlignment = Alignment.Center,
            ) {
                TileIcon(Icons.Filled.Person, PanelDimens.iconSmall, Color.White)
            }
        }
    }
}
