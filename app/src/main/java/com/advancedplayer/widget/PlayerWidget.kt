package com.advancedplayer.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.advancedplayer.AdvancedPlayerApp
import com.advancedplayer.AppContainer
import com.advancedplayer.MainActivity
import com.advancedplayer.R
import com.advancedplayer.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val PREFS = "widget"

class PlayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString("title", null)
        val artist = prefs.getString("artist", null).orEmpty()
        val playing = prefs.getBoolean("playing", false)
        val trackId = prefs.getLong("track", -1L)
        val container = (context.applicationContext as AdvancedPlayerApp).container
        val art = if (trackId >= 0) container.art.bitmap(trackId, 256) else null
        val accent = Color(container.settings.current().accent)
        val fallbackTitle = context.getString(R.string.nothing_playing)
        provideContent { WidgetContent(title ?: fallbackTitle, artist, playing, art, accent) }
    }

    @Composable
    private fun WidgetContent(title: String, artist: String, playing: Boolean, art: Bitmap?, accent: Color) {
        val white = ColorProvider(Color.White)
        Row(
            modifier = GlanceModifier.fillMaxSize().background(Color(0xF2181818)).cornerRadius(20.dp).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.size(56.dp).cornerRadius(10.dp).background(Color(0xFF2A2A2A))
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(ImageProvider(art), contentDescription = null, modifier = GlanceModifier.size(56.dp).cornerRadius(10.dp))
                } else {
                    Image(
                        ImageProvider(R.drawable.ic_notification), contentDescription = null,
                        modifier = GlanceModifier.size(28.dp), colorFilter = ColorFilter.tint(ColorProvider(accent)),
                    )
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, maxLines = 1, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                if (artist.isNotEmpty()) {
                    Text(artist, maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFFB3B3B3)), fontSize = 13.sp))
                }
            }
            ControlButton(R.drawable.ic_w_prev, "prev", Color.White, 40)
            ControlButton(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, "toggle", accent, 48)
            ControlButton(R.drawable.ic_w_next, "next", Color.White, 40)
        }
    }

    @Composable
    private fun ControlButton(icon: Int, command: String, tint: Color, size: Int) {
        Box(
            modifier = GlanceModifier.size(size.dp).clickable(actionRunCallback<WidgetAction>(actionParametersOf(CommandKey to command))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(icon), contentDescription = command,
                modifier = GlanceModifier.size((size * 0.6).dp), colorFilter = ColorFilter.tint(ColorProvider(tint)),
            )
        }
    }

    companion object {
        val CommandKey = ActionParameters.Key<String>("cmd")

        fun publish(context: Context, container: AppContainer, item: MediaItem?, playing: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                if (item == null) {
                    remove("title"); remove("artist"); remove("track")
                } else {
                    putString("title", item.mediaMetadata.title?.toString())
                    putString("artist", item.mediaMetadata.artist?.toString())
                    putLong("track", item.mediaId.toLongOrNull() ?: -1L)
                }
                putBoolean("playing", playing)
            }
            container.appScope.launch { runCatching { PlayerWidget().updateAll(context) } }
        }
    }
}

class WidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().await()
        try {
            when (parameters[PlayerWidget.CommandKey]) {
                "toggle" -> if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controller.play()
                }
                "next" -> controller.seekToNext()
                "prev" -> controller.seekToPrevious()
            }
            delay(400)
        } finally {
            controller.release()
        }
    }
}

class PlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayerWidget()
}
