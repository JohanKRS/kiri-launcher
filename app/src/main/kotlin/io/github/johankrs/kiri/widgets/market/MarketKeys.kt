package io.github.johankrs.kiri.widgets.market

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.market.ApiKeys
import io.github.johankrs.kiri.market.KeyCheck
import io.github.johankrs.kiri.market.KeyService
import io.github.johankrs.kiri.market.MarketFormat
import io.github.johankrs.kiri.market.MarketRepository
import io.github.johankrs.kiri.market.MarketSource
import io.github.johankrs.kiri.ui.settings.Glyph
import io.github.johankrs.kiri.ui.settings.GlyphIcon
import io.github.johankrs.kiri.ui.settings.SectionTitle
import io.github.johankrs.kiri.ui.settings.SettingsGroup
import io.github.johankrs.kiri.ui.settings.SettingsScaffold
import io.github.johankrs.kiri.ui.theme.KiriTheme
import io.github.johankrs.kiri.ui.theme.KiriType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One part of a key, as the service shows it: Alpaca gives two, the others one. */
private class KeyPart(
    @StringRes val label: Int,
    val read: (ApiKeys) -> String,
    val write: (ApiKeys, String) -> ApiKeys,
)

/** What the guide for one service says, and where its sign-up page is. */
private class KeyGuide(
    val service: KeyService,
    /** The service's own name, the title of its guide. */
    @StringRes val name: Int,
    /** What it gives, as its row in the list says it. */
    @StringRes val row: Int,
    /** The row's second line while there is no key. */
    @StringRes val without: Int,
    @StringRes val intro: Int,
    val signUp: String,
    @StringRes val site: Int,
    @StringRes val createText: Int,
    @StringRes val copyTitle: Int,
    @StringRes val copyText: Int,
    val parts: List<KeyPart>,
)

private val Guides = listOf(
    KeyGuide(
        service = KeyService.BRAPI,
        name = R.string.market_keys_brapi,
        row = R.string.market_keys_brapi_row,
        without = R.string.market_keys_brapi_without,
        intro = R.string.market_keys_brapi_intro,
        signUp = "https://brapi.dev/dashboard",
        site = R.string.market_keys_brapi_site,
        createText = R.string.market_keys_brapi_create,
        copyTitle = R.string.market_keys_copy_token,
        copyText = R.string.market_keys_brapi_copy,
        parts = listOf(KeyPart(R.string.market_keys_token, { it.brapi }, { keys, v -> keys.copy(brapi = v) })),
    ),
    KeyGuide(
        service = KeyService.ALPACA,
        name = R.string.market_keys_alpaca,
        row = R.string.market_keys_alpaca_row,
        without = R.string.market_keys_alpaca_without,
        intro = R.string.market_keys_alpaca_intro,
        signUp = "https://app.alpaca.markets/signup",
        site = R.string.market_keys_alpaca_site,
        createText = R.string.market_keys_alpaca_create,
        copyTitle = R.string.market_keys_make_key,
        copyText = R.string.market_keys_alpaca_copy,
        parts = listOf(
            KeyPart(R.string.market_keys_alpaca_key, { it.alpacaId }, { keys, v -> keys.copy(alpacaId = v) }),
            KeyPart(R.string.market_keys_alpaca_secret, { it.alpacaSecret }, { keys, v -> keys.copy(alpacaSecret = v) }),
        ),
    ),
    KeyGuide(
        service = KeyService.FINNHUB,
        name = R.string.market_keys_finnhub,
        row = R.string.market_keys_finnhub_row,
        without = R.string.market_keys_finnhub_without,
        intro = R.string.market_keys_finnhub_intro,
        signUp = "https://finnhub.io/register",
        site = R.string.market_keys_finnhub_site,
        createText = R.string.market_keys_finnhub_create,
        copyTitle = R.string.market_keys_copy_key,
        copyText = R.string.market_keys_finnhub_copy,
        parts = listOf(KeyPart(R.string.market_keys_part_key, { it.finnhub }, { keys, v -> keys.copy(finnhub = v) })),
    ),
    KeyGuide(
        service = KeyService.AWESOME,
        name = R.string.market_keys_awesome,
        row = R.string.market_keys_awesome_row,
        without = R.string.market_keys_awesome_without,
        intro = R.string.market_keys_awesome_intro,
        signUp = "https://awesomeapi.com.br/auth/signup",
        site = R.string.market_keys_awesome_site,
        createText = R.string.market_keys_awesome_create,
        copyTitle = R.string.market_keys_copy_key,
        copyText = R.string.market_keys_awesome_copy,
        parts = listOf(KeyPart(R.string.market_keys_part_key, { it.awesome }, { keys, v -> keys.copy(awesome = v) })),
    ),
)

private fun guideOf(service: KeyService): KeyGuide = Guides.first { it.service == service }

private fun KeyGuide.saved(keys: ApiKeys): Boolean = parts.all { it.read(keys).isNotBlank() }

/**
 * Where the prices come from: a choice between the complete sources this build has and the
 * official services, when it has any, and then the official services with the state of each
 * key. A build without complete sources has nothing to choose, so it shows only the services.
 */
@Composable
internal fun MarketSourcesSection(
    prefs: MarketPrefs,
    repository: MarketRepository,
    onChange: (MarketPrefs) -> Unit,
    onOpenGuide: (KeyService) -> Unit,
) {
    val colors = KiriTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val complete = repository.completeSources

    SectionTitle(stringResource(R.string.market_section_source))
    if (complete.isNotEmpty()) {
        val inUse = repository.resolve(prefs.settings.source)
        SettingsGroup(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
                LightChoices(
                    options = complete.map { MarketSource(it.key) to it.name(locale) } +
                        (MarketSource.OFFICIAL to stringResource(R.string.market_source_official)),
                    selected = inUse,
                    onSelect = { onChange(prefs.copy(settings = prefs.settings.copy(source = it))) },
                )
                BasicText(
                    text = repository.completeSource(inUse)?.note(locale)
                        ?: stringResource(R.string.market_source_official_note),
                    style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp),
                )
            }
        }
        SectionTitle(stringResource(R.string.market_section_services))
    } else {
        BasicText(
            text = stringResource(R.string.market_services_intro),
            style = KiriType.caption.copy(color = colors.onSurfaceMuted),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
        )
    }
    SettingsGroup(Modifier.fillMaxWidth()) {
        for (guide in Guides) {
            val saved = guide.saved(prefs.keys)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpenGuide(guide.service) }
                    .heightIn(min = 56.dp)
                    .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(text = stringResource(guide.row), style = KiriType.body.copy(color = colors.onSurface))
                    if (saved) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.CHECK, colors.accent, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                text = stringResource(R.string.market_keys_saved),
                                style = KiriType.caption.copy(color = colors.accent),
                            )
                        }
                    } else {
                        BasicText(
                            text = stringResource(guide.without),
                            style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                GlyphIcon(Glyph.FORWARD, colors.onSurfaceMuted, Modifier.size(18.dp))
            }
        }
    }
    BasicText(
        text = stringResource(R.string.market_keys_note),
        style = KiriType.caption.copy(color = colors.onSurfaceMuted),
        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
}

/** Where a key stands on the guide's screen. */
private sealed interface Trial {
    data object Idle : Trial

    data object Testing : Trial

    data class Done(val check: KeyCheck) : Trial
}

/**
 * The step-by-step guide for one service's key: open its sign-up page, copy the key there, and
 * paste it here. A pasted key is tried at once on a real price, and kept unless the service
 * refuses it, so a wrong key never reaches the widget.
 */
@Composable
internal fun MarketKeyGuide(
    service: KeyService,
    prefs: MarketPrefs,
    repository: MarketRepository,
    onChange: (MarketPrefs) -> Unit,
    onBack: () -> Unit,
    /** For previews: the result to show without asking the service. */
    initialTrial: KeyCheck? = null,
) {
    val guide = guideOf(service)
    val colors = KiriTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val uris = LocalUriHandler.current
    val copiedSince = rememberCopiedSince()
    val saved = guide.saved(prefs.keys)

    var drafts by remember(service) { mutableStateOf(guide.parts.map { it.read(prefs.keys) }) }
    var trial by remember(service) { mutableStateOf<Trial>(initialTrial?.let { Trial.Done(it) } ?: Trial.Idle) }
    // Once the key works, the first two steps have nothing left to say.
    val done = saved && (trial as? Trial.Done)?.check !is KeyCheck.Refused

    fun tryDrafts() {
        if (drafts.any { it.isBlank() }) return
        val keys = guide.parts.zip(drafts).fold(prefs.keys) { keys, (part, value) -> part.write(keys, value.trim()) }
        trial = Trial.Testing
        scope.launch {
            val check = withContext(Dispatchers.IO) { repository.check(service, keys) }
            trial = Trial.Done(check)
            if (check !is KeyCheck.Refused) onChange(prefs.copy(keys = keys))
        }
    }

    SettingsScaffold(title = stringResource(guide.name), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BasicText(
                text = stringResource(guide.intro),
                style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
            )
            SectionTitle(stringResource(R.string.market_keys_steps))
            SettingsGroup(Modifier.fillMaxWidth()) {
                Step(number = 1, done = done, title = stringResource(R.string.market_keys_create)) {
                    if (!done) {
                        StepText(stringResource(guide.createText))
                        val site = stringResource(guide.site)
                        TextAction(
                            text = stringResource(R.string.market_keys_open, site),
                            mark = KeyMark.OUT,
                            onClick = {
                                copiedSince.leaving()
                                runCatching { uris.openUri(guide.signUp) }
                            },
                        )
                    }
                }
                Step(number = 2, done = done, title = stringResource(guide.copyTitle)) {
                    if (!done) StepText(stringResource(guide.copyText))
                }
                Step(number = 3, done = done, title = stringResource(R.string.market_keys_paste_here)) {
                    guide.parts.forEachIndexed { index, part ->
                        KeyField(
                            label = stringResource(part.label),
                            value = drafts[index],
                            onValueChange = { typed ->
                                drafts = drafts.toMutableList().also { it[index] = typed }
                                if (trial !is Trial.Testing) trial = Trial.Idle
                            },
                            onPaste = {
                                scope.launch {
                                    val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                    val pasted = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                    if (pasted.isNotEmpty()) {
                                        drafts = drafts.toMutableList().also { it[index] = pasted }
                                        tryDrafts()
                                    }
                                }
                            },
                            onDone = ::tryDrafts,
                        )
                    }
                    if (copiedSince.copied && !done && trial == Trial.Idle) {
                        BasicText(
                            text = stringResource(R.string.market_keys_copied_hint),
                            style = KiriType.caption.copy(color = colors.accent),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                when (val shown = trial) {
                    Trial.Idle -> Unit
                    Trial.Testing -> Result(
                        mark = ResultMark.TESTING,
                        title = stringResource(R.string.market_keys_testing),
                        text = stringResource(R.string.market_keys_testing_on, service.sample),
                    )
                    is Trial.Done -> CheckResult(shown.check, guide)
                }
            }
            if (saved && trial !is Trial.Testing) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(start = 6.dp, top = 4.dp)) {
                    TextAction(text = stringResource(R.string.market_keys_test_again), onClick = {
                        drafts = guide.parts.map { it.read(prefs.keys) }
                        tryDrafts()
                    })
                    TextAction(text = stringResource(R.string.market_keys_forget), muted = true, onClick = {
                        val cleared = guide.parts.fold(prefs.keys) { keys, part -> part.write(keys, "") }
                        drafts = guide.parts.map { "" }
                        trial = Trial.Idle
                        onChange(prefs.copy(keys = cleared))
                    })
                }
            }
            BasicText(
                text = stringResource(R.string.market_keys_note),
                style = KiriType.caption.copy(color = colors.onSurfaceMuted),
                modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CheckResult(check: KeyCheck, guide: KeyGuide) {
    val locale = LocalConfiguration.current.locales[0]
    val name = stringResource(guide.name)
    when (check) {
        is KeyCheck.Works -> {
            val quote = check.sample
            Result(
                mark = ResultMark.WORKS,
                title = stringResource(R.string.market_keys_works),
                text = stringResource(
                    R.string.market_keys_works_sample,
                    quote.instrument.symbol,
                    MarketFormat.price(quote.price, quote.currency, locale),
                ),
            )
        }
        KeyCheck.Refused -> Result(
            mark = ResultMark.PROBLEM,
            title = stringResource(R.string.market_keys_refused, name),
            text = stringResource(R.string.market_keys_refused_text),
        )
        KeyCheck.OutOfRequests -> Result(
            mark = ResultMark.PROBLEM,
            title = stringResource(R.string.market_keys_limit),
            text = stringResource(R.string.market_keys_limit_text),
        )
        KeyCheck.Offline -> Result(
            mark = ResultMark.OFFLINE,
            title = stringResource(R.string.market_keys_offline),
            text = stringResource(R.string.market_keys_offline_text),
        )
        KeyCheck.Unclear -> Result(
            mark = ResultMark.OFFLINE,
            title = stringResource(R.string.market_keys_unclear),
            text = stringResource(R.string.market_keys_unclear_text),
        )
    }
}

/** One step: its number in a ring (a tick once it is done), what to do, and anything below. */
@Composable
private fun Step(number: Int, done: Boolean, title: String, content: @Composable () -> Unit) {
    val colors = KiriTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
                .border(1.4.dp, if (done) colors.accent else colors.onSurfaceMuted, CircleShape),
        ) {
            if (done) {
                GlyphIcon(Glyph.CHECK, colors.accent, Modifier.size(13.dp))
            } else {
                BasicText(text = number.toString(), style = KiriType.caption.copy(color = colors.onSurfaceMuted))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(text = title, style = KiriType.body.copy(color = colors.onSurface))
            content()
        }
    }
}

@Composable
private fun StepText(text: String) {
    BasicText(
        text = text,
        style = KiriType.caption.copy(color = KiriTheme.colors.onSurfaceMuted),
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** An action with no button behind it: its words, in the accent, and a small mark before them. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit, mark: KeyMark? = null, muted: Boolean = false) {
    val colors = KiriTheme.colors
    val color = if (muted) colors.onSurfaceMuted else colors.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (mark != null) {
            KeyMarkIcon(mark, color, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        BasicText(text = text, style = KiriType.body.copy(color = color, fontWeight = FontWeight.Medium))
    }
}

/**
 * A field for one part of a key. It is meant to be pasted into, with the action beside it, but
 * it takes typing too. Away from the keyboard a key shows only its ends, so a glance over the
 * shoulder does not read it.
 */
@Composable
private fun KeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = KiriTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        BasicText(
            text = label,
            style = KiriType.caption.copy(color = colors.onSurfaceMuted),
            modifier = Modifier.widthIn(min = 48.dp).padding(end = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = KiriType.body.copy(color = colors.onSurface, fontFeatureSettings = "tnum"),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (focused) VisualTransformation.None else KeyMask,
            interactionSource = interactions,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            decorationBox = { field ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    if (value.isEmpty()) {
                        BasicText(
                            text = stringResource(R.string.market_key_hint),
                            style = KiriType.body.copy(color = colors.onSurfaceMuted),
                        )
                    }
                    field()
                }
            },
        )
        Spacer(Modifier.width(10.dp))
        TextAction(text = stringResource(R.string.market_keys_paste), mark = KeyMark.PASTE, onClick = onPaste)
    }
}

/**
 * Shows only the first and last four characters of a key, with a few dots between them, so even
 * a long secret fits the field and its end can be checked against the site.
 */
private object KeyMask : VisualTransformation {
    private const val ENDS = 4
    private const val DOTS = 6

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= 2 * ENDS + 2) {
            return TransformedText(AnnotatedString("•".repeat(raw.length)), OffsetMapping.Identity)
        }
        val hidden = raw.length - 2 * ENDS
        val dots = minOf(hidden, DOTS)
        val tail = raw.length - ENDS
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= ENDS -> offset
                offset >= tail -> offset - hidden + dots
                else -> ENDS + (offset - ENDS) * dots / hidden
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= ENDS -> offset
                offset >= ENDS + dots -> offset - dots + hidden
                else -> ENDS + (offset - ENDS) * hidden / dots
            }
        }
        val shown = raw.take(ENDS) + "•".repeat(dots) + raw.takeLast(ENDS)
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

private enum class ResultMark { TESTING, WORKS, PROBLEM, OFFLINE }

/** The last row of the guide: what came of trying the key. */
@Composable
private fun Result(mark: ResultMark, title: String, text: String) {
    val colors = KiriTheme.colors
    // A soft red that reads on either theme, for the one thing that needs the person.
    val warn = if (colors.surface.luminance() < 0.5f) Color(0xFFE7A48F) else Color(0xFFA4442E)
    val color = when (mark) {
        ResultMark.WORKS -> colors.accent
        ResultMark.PROBLEM -> warn
        ResultMark.TESTING, ResultMark.OFFLINE -> colors.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (mark) {
                ResultMark.TESTING -> Turning(colors.accent)
                ResultMark.WORKS -> GlyphIcon(Glyph.CHECK, color, Modifier.size(18.dp))
                ResultMark.PROBLEM -> KeyMarkIcon(KeyMark.ALERT, color, Modifier.size(18.dp))
                ResultMark.OFFLINE -> KeyMarkIcon(KeyMark.DASHED, colors.onSurfaceMuted, Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(text = title, style = KiriType.body.copy(color = color))
            BasicText(text = text, style = KiriType.caption.copy(color = colors.onSurfaceMuted), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** A ring with a gap, turning while the key is tried. */
@Composable
private fun Turning(color: Color) {
    val turn by rememberInfiniteTransition(label = "trying").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    Canvas(
        Modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = turn },
    ) {
        drawArc(color, startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

private enum class KeyMark { OUT, PASTE, ALERT, DASHED }

@Composable
private fun KeyMarkIcon(mark: KeyMark, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color = color,
            start = Offset(x1 * w, y1 * h),
            end = Offset(x2 * w, y2 * h),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        when (mark) {
            // A box with an arrow leaving it: opens somewhere outside the app.
            KeyMark.OUT -> {
                line(0.42f, 0.2f, 0.22f, 0.2f)
                line(0.2f, 0.22f, 0.2f, 0.78f)
                line(0.22f, 0.8f, 0.78f, 0.8f)
                line(0.8f, 0.78f, 0.8f, 0.58f)
                line(0.55f, 0.2f, 0.8f, 0.2f)
                line(0.8f, 0.2f, 0.8f, 0.45f)
                line(0.8f, 0.2f, 0.46f, 0.54f)
            }
            // A board with a clip and two lines on it.
            KeyMark.PASTE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0.25f * w, 0.22f * h),
                    size = Size(0.5f * w, 0.62f * h),
                    cornerRadius = CornerRadius(0.1f * w),
                    style = Stroke(stroke),
                )
                line(0.4f, 0.17f, 0.6f, 0.17f)
                line(0.38f, 0.45f, 0.62f, 0.45f)
                line(0.38f, 0.6f, 0.55f, 0.6f)
            }
            KeyMark.ALERT -> {
                drawCircle(color, radius = 0.38f * w, style = Stroke(stroke))
                line(0.5f, 0.3f, 0.5f, 0.56f)
                drawCircle(color, radius = stroke * 0.7f, center = Offset(0.5f * w, 0.69f * h))
            }
            KeyMark.DASHED -> for (i in 0 until 8) {
                drawArc(color, startAngle = i * 45f, sweepAngle = 24f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(0.12f * w, 0.12f * h), size = Size(0.76f * w, 0.76f * h))
            }
        }
    }
}

/**
 * Choices where exactly one is on, with nothing drawn behind them: the one in use is written
 * heavier with a short line under it, as on the widget's panes.
 */
@Composable
private fun <T> LightChoices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = KiriTheme.colors
    ChoiceFlow(slots = 0, gap = 2.dp, modifier = Modifier.fillMaxWidth()) {
        for ((value, label) in options) {
            val on = value == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Keeps the room of the heavier writing, so choosing never moves the others.
                    BasicText(text = label, style = KiriType.body.copy(fontWeight = FontWeight.SemiBold, color = Color.Transparent), softWrap = false)
                    BasicText(
                        text = label,
                        style = KiriType.body.copy(
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) colors.onSurface else colors.onSurfaceMuted,
                        ),
                        softWrap = false,
                    )
                }
                Box(
                    Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(if (on) colors.accent else Color.Transparent),
                )
            }
        }
    }
}

/**
 * Whether something was copied since the person left for a sign-up page, so the guide can
 * point at Paste on the way back. Only the clipboard's description is read, never its text:
 * reading the text is left to the person's own tap on Paste.
 */
private class CopiedSince(private val clipboard: ClipboardManager?) {
    private var leftAt by mutableLongStateOf(Long.MAX_VALUE)
    var copied by mutableStateOf(false)
        private set

    fun leaving() {
        leftAt = System.currentTimeMillis()
        copied = false
    }

    fun back() {
        val at = runCatching { clipboard?.primaryClipDescription?.timestamp }.getOrNull() ?: return
        copied = at >= leftAt
    }
}

@Composable
private fun rememberCopiedSince(): CopiedSince {
    val context = LocalContext.current
    val since = remember { CopiedSince(context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) since.back() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return since
}
