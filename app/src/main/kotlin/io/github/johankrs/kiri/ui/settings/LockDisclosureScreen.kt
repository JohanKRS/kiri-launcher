package io.github.johankrs.kiri.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType

/**
 * What Kiri's accessibility service is for, shown every time before Android's accessibility
 * settings are opened for it, as Google Play asks of an app that is not an accessibility tool:
 * in the app's own words, apart from anything else, and with a choice the person makes. Only
 * [onAllow] goes on to Android; [onDecline] (and Back) leaves everything as it was.
 *
 * [forGesture] is the gesture being set to lock the screen, when that is what brought it here;
 * null when a gesture already locks it and the service is only being turned on.
 */
@Composable
fun LockDisclosureScreen(
    forGesture: String?,
    onAllow: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = KiriTheme.colors
    SettingsScaffold(title = stringResource(R.string.lock_disclosure_title), onBack = onDecline) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                text = stringResource(R.string.lock_disclosure_intro),
                style = KiriType.body.copy(color = colors.onSurface),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            SettingsGroup {
                InfoCard(
                    title = stringResource(R.string.lock_disclosure_does_title),
                    text = stringResource(R.string.lock_disclosure_does),
                )
                InfoCard(
                    title = stringResource(R.string.lock_disclosure_not_title),
                    text = stringResource(R.string.lock_disclosure_not),
                )
                InfoCard(
                    title = stringResource(R.string.lock_disclosure_off_title),
                    text = stringResource(R.string.lock_disclosure_off),
                )
            }
            Spacer(Modifier.height(4.dp))
            SettingsGroup {
                DisclosureChoice(
                    title = stringResource(R.string.lock_disclosure_allow),
                    summary = stringResource(R.string.lock_disclosure_allow_summary),
                    primary = true,
                    onClick = onAllow,
                )
                DisclosureChoice(
                    title = stringResource(R.string.lock_disclosure_decline),
                    summary = stringResource(
                        if (forGesture != null) R.string.lock_disclosure_decline_summary else R.string.lock_disclosure_decline_summary_off,
                    ),
                    primary = false,
                    onClick = onDecline,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * One of the answers to a question such as whether to allow a service: the one that goes on
 * ([primary]) has its words in the accent and an arrow.
 */
@Composable
fun DisclosureChoice(title: String, summary: String, primary: Boolean, onClick: () -> Unit) {
    val colors = KiriTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = KiriType.body.copy(
                    color = if (primary) colors.accent else colors.onSurface,
                    fontWeight = if (primary) FontWeight.Medium else KiriType.body.fontWeight,
                ),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(text = summary, style = KiriType.caption.copy(color = colors.onSurfaceMuted))
        }
        if (primary) {
            Spacer(Modifier.width(12.dp))
            GlyphIcon(Glyph.FORWARD, colors.accent, Modifier.size(18.dp))
        }
    }
}
