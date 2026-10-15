package com.music.konnekt.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.music.konnekt.R
import com.music.konnekt.data.AppUpdateChecker
import com.music.konnekt.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** UIAlertController's own metrics: fixed narrow width, 14pt corner, 44pt rows. */
internal val ALERT_WIDTH = 270.dp
internal val ALERT_CORNER = 14.dp
internal val ACTION_HEIGHT = 44.dp

/**
 * The dim behind the alert. Flat on purpose — the glass is the card, and
 * blurring the wallpaper *behind* it too leaves nothing for the card to be
 * frosted against, which is what made this read as a grey box before.
 */
internal val SCRIM_COLOR = Color.Black.copy(alpha = 0.28f)

/** Native dialog with a short entrance and a persistent, cancellable download. */
@Composable
fun UpdateAvailableDialog(
    version: String,
    notes: String?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by AppUpdateChecker.download.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val entrance = remember { Animatable(if (reduceAnimation) 1f else 0f) }
    LaunchedEffect(reduceAnimation) {
        if (reduceAnimation) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    val context = LocalContext.current
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val downloading = state as? AppUpdateChecker.DownloadState.Downloading
    val progress by animateFloatAsState(
        downloading?.fraction?.coerceIn(0f, 1f) ?: 1f,
        animationSpec = tween(if (reduceAnimation) 0 else 180), label = "updateProgress",
    )
    val body = stringResource(when (state) {
        is AppUpdateChecker.DownloadState.Downloading -> R.string.update_downloading_body
        is AppUpdateChecker.DownloadState.Ready -> R.string.update_ready_body
        is AppUpdateChecker.DownloadState.Failed -> R.string.update_failed_body
        AppUpdateChecker.DownloadState.Verifying -> R.string.update_verifying_body
        else -> R.string.update_available_body
    }, version)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth()
                .heightIn(max = maxHeight)
                .graphicsLayer {
                    alpha = 0.3f + entrance.value * 0.7f
                    scaleX = 0.96f + entrance.value * 0.04f
                    scaleY = scaleX
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.launcher_logo_image), contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("Konnekt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("v$version", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.software_update), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (downloading != null || state == AppUpdateChecker.DownloadState.Verifying) {
                    Spacer(Modifier.height(20.dp))
                    if (state == AppUpdateChecker.DownloadState.Verifying || (downloading?.fraction ?: 0f) < 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    if (downloading != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Formatter.formatShortFileSize(context, downloading.bytes), style = MaterialTheme.typography.labelMedium)
                            if (downloading.total > 0) Text(
                                NumberFormat.getPercentInstance().format(downloading.fraction.coerceIn(0f, 1f)),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (state is AppUpdateChecker.DownloadState.Failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(if ((state as AppUpdateChecker.DownloadState.Failed).invalidApk)
                        R.string.update_invalid_apk else R.string.update_retry_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (state is AppUpdateChecker.DownloadState.Ready && !context.packageManager.canRequestPackageInstalls()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.update_install_permission), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!notes.isNullOrBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 20.dp))
                    Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Box(Modifier.padding(top = 8.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            RichText(style = RichTextStyle.Default) { Markdown(notes) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                when (state) {
                    is AppUpdateChecker.DownloadState.Downloading -> {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.later)) }
                        TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                    }
                    AppUpdateChecker.DownloadState.Verifying -> {
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.update_verifying))
                        }
                    }
                    else -> {
                        val ready = state is AppUpdateChecker.DownloadState.Ready
                        Button(onClick = if (ready) onInstall else onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(if (ready) R.string.install_now
                                else if (state is AppUpdateChecker.DownloadState.Failed) R.string.try_again else R.string.download_now))
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.remind_me_later))
                        }
                    }
                }
            }
        }
    }
}
/**
 * Full-bleed action row. Tinted rather than filled, so the two read as equals
 * in weight and only the font differentiates the default action — the alert's
 * whole point is that neither choice is a trap.
 *
 * [destructive] is the one exception to that: an action that deletes something
 * is red in this lineage, because the point there *is* to break the symmetry.
 * It stays a plain row rather than a filled button, so it reads as a warning
 * and not as the thing to press.
 */
@Composable
internal fun AlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = if (emphasised) FontWeight.W600 else FontWeight.W400,
            ),
            color = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** Hairline separator — [HorizontalDivider][androidx.compose.material3.HorizontalDivider]'s 1dp reads as a bar at this scale. */
@Composable
internal fun AlertRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}
