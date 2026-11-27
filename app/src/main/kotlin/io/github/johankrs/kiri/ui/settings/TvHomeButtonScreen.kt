package io.github.johankrs.kiri.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.tv.HomeRoute
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

/** Where the screen of the Home button stands, from what Kiri found out. */
enum class HomeButtonStep {
    /** The Home button opens Kiri: through Android, or with Kiri coming in place of the other one. */
    DONE,

    /** Android lets the person choose: its own way first. */
    ASK_ANDROID,

    /**
     * Only Kiri coming in place of the other home screen helps: that one outranks every choice,
     * or Android was asked and kept it. What the service is for comes first, as Google Play asks.
     */
    TAKE_OVER,

    /** Allowed in Kiri, not turned on in Android's accessibility settings yet. */
    WAITING,
}

/**
 * Makes Kiri what the Home button of the TV's remote opens. Android's own way comes first (the
 * request for the home screen, or its settings of default apps); where the TV's own home screen
 * outranks every choice, as Google TV's does, Kiri offers to come in its place instead, through
 * its accessibility service, after saying what the service does and does not do.
 *
 * [step] is where it stands; [route] is how Kiri can become the home screen at all, [asked]
 * whether Android was asked and kept the other one. [otherName] and [otherIcon] are the home
 * screen the button opens instead of Kiri, and [kiriIcon] is Kiri's.
 * [onAskAndroid] is null where Android has no way to ask; [askOpensSettings] when that way is its
 * settings rather than a question.
 */
@Composable
fun TvHomeButtonScreen(
    step: HomeButtonStep,
    route: HomeRoute,
    asked: Boolean,
    takenOver: Boolean,
    otherName: String?,
    otherIcon: Drawable?,
    kiriIcon: Drawable?,
    onAskAndroid: (() -> Unit)?,
    askOpensSettings: Boolean,
    onAllow: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onTurnOff: () -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp
    val inline = otherName ?: stringResource(R.string.tv_home_tv_own_inline)
    val status: @Composable (HomeButtonStep, Modifier) -> Unit = { at, modifier ->
        val opensKiri = at == HomeButtonStep.DONE
        val note = when {
            opensKiri && takenOver -> stringResource(R.string.tv_home_note_taken_over, inline)
            opensKiri -> stringResource(R.string.tv_home_note_kiri)
            asked -> stringResource(R.string.tv_home_note_asked, inline)
            route == HomeRoute.TAKE_OVER -> stringResource(R.string.tv_home_note_locked, inline)
            else -> stringResource(R.string.tv_home_note_ask)
        }
        HomeOpens(
            name = if (opensKiri) stringResource(R.string.app_name) else otherName ?: stringResource(R.string.tv_home_tv_own),
            icon = if (opensKiri) kiriIcon else otherIcon,
            note = note,
            modifier = modifier,
        )
    }

    val actions: @Composable (HomeButtonStep, Modifier) -> Unit = { at, modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (at) {
                HomeButtonStep.DONE -> if (takenOver) {
                    SettingsGroup {
                        InfoCard(
                            title = stringResource(R.string.tv_home_on_title),
                            text = stringResource(R.string.tv_home_on, inline),
                        )
                        DisclosureChoice(
                            title = stringResource(R.string.tv_home_turn_off),
                            summary = stringResource(R.string.tv_home_turn_off_summary, inline),
                            primary = false,
                            onClick = onTurnOff,
                        )
                    }
                }

                HomeButtonStep.ASK_ANDROID -> if (onAskAndroid != null) {
                    SectionTitle(stringResource(R.string.tv_home_official))
                    SettingsGroup {
                        DisclosureChoice(
                            title = stringResource(if (askOpensSettings) R.string.tv_home_ask_settings else R.string.tv_home_ask),
                            summary = stringResource(
                                if (askOpensSettings) R.string.tv_home_ask_settings_summary else R.string.tv_home_ask_summary,
                            ),
                            primary = true,
                            onClick = onAskAndroid,
                        )
                    }
                }

                HomeButtonStep.TAKE_OVER -> {
                    SectionTitle(stringResource(R.string.tv_home_takeover))
                    BasicText(
                        text = stringResource(R.string.tv_home_takeover_intro),
                        style = KiriType.body.copy(color = KiriTheme.colors.onSurface),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    SettingsGroup {
                        InfoCard(
                            title = stringResource(R.string.tv_home_does_title),
                            text = stringResource(R.string.tv_home_does, inline),
                        )
                        InfoCard(
                            title = stringResource(R.string.tv_home_not_title),
                            text = stringResource(R.string.tv_home_not),
                        )
                        InfoCard(
                            title = stringResource(R.string.tv_home_off_title),
                            text = stringResource(R.string.tv_home_off, inline),
                        )
                    }
                    SettingsGroup {
                        DisclosureChoice(
                            title = stringResource(R.string.tv_home_allow),
                            summary = stringResource(R.string.tv_home_allow_summary),
                            primary = true,
                            onClick = onAllow,
                        )
                        DisclosureChoice(
                            title = stringResource(R.string.tv_home_decline),
                            summary = stringResource(R.string.tv_home_decline_summary, inline),
                            primary = false,
                            onClick = onBack,
                        )
                    }
                }

                HomeButtonStep.WAITING -> SettingsGroup {
                    InfoCard(
                        title = stringResource(R.string.tv_home_waiting_title),
                        text = stringResource(R.string.tv_home_waiting),
                    )
                    DisclosureChoice(
                        title = stringResource(R.string.tv_home_open_accessibility),
                        summary = stringResource(R.string.tv_home_open_accessibility_summary),
                        primary = true,
                        onClick = onOpenAccessibility,
                    )
                    DisclosureChoice(
                        title = stringResource(R.string.tv_home_turn_off),
                        summary = stringResource(R.string.tv_home_turn_off_summary, inline),
                        primary = false,
                        onClick = onTurnOff,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    SettingsScaffold(title = stringResource(R.string.tv_home_title), onBack = onBack, focusFirst = true) {
        // One step fades into the next, without the screen around it moving.
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "home button",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            if (wide) {
                // A TV: what the button opens on the left, what to do about it on the right.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    status(
                        shown,
                        Modifier
                            .weight(1f)
                            .padding(top = 6.dp),
                    )
                    actions(
                        shown,
                        Modifier
                            .weight(1.25f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    status(shown, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    actions(
                        shown,
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * What the Home button opens now: the button, an arrow and that home screen, with [note] under
 * them saying why, or what happens.
 */
@Composable
private fun HomeOpens(name: String, icon: Drawable?, note: String, modifier: Modifier = Modifier) {
    val colors = KiriTheme.colors
    SettingsGroup(modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            BasicText(
                text = stringResource(R.string.tv_home_opens).uppercase(),
                style = KiriType.section.copy(color = colors.onSurfaceMuted),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The Home button, as round as on a remote.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.control),
                ) {
                    GlyphIcon(Glyph.HOME, colors.onSurface, Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                GlyphIcon(Glyph.FORWARD, colors.onSurfaceMuted, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                AppMark(icon, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = name,
                    style = KiriType.title.copy(color = colors.onSurface),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(16.dp))
            BasicText(text = note, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
        }
    }
}

/** An app's icon from Android, drawn once at its size; an empty round place when there is none. */
@Composable
private fun AppMark(icon: Drawable?, modifier: Modifier) {
    val density = LocalDensity.current
    val px = with(density) { 52.dp.roundToPx() }
    val picture: ImageBitmap? = remember(icon, px) {
        icon?.let { runCatching { it.toBitmap(px, px).asImageBitmap() }.getOrNull() }
    }
    if (picture != null) {
        Image(bitmap = picture, contentDescription = null, modifier = modifier)
    } else {
        Box(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(KiriTheme.colors.control),
        )
    }
}
