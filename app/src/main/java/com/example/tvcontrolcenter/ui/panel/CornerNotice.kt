package com.example.tvcontrolcenter.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.domain.model.AppNotification
import com.example.tvcontrolcenter.ui.theme.PanelType

/**
 * A new notification in the corner of the screen: the app, then the notification's own title and a
 * line or two of its text, on a dark card. Drawn outside the panel, so it follows the panel's
 * scale ([ProvideUiScale][com.example.tvcontrolcenter.ui.theme.ProvideUiScale]) rather than size.
 */
@Composable
internal fun CornerNoticeCard(notice: AppNotification) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // An app that titles its notification with its own name would say it twice.
        if (notice.appLabel != notice.title) {
            BasicText(
                notice.appLabel,
                style = PanelType.labelSmall.copy(color = Color.White.copy(alpha = 0.62f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            notice.title,
            style = PanelType.label.copy(color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!notice.text.isNullOrBlank()) {
            BasicText(
                notice.text,
                style = PanelType.detail.copy(color = Color.White.copy(alpha = 0.82f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
