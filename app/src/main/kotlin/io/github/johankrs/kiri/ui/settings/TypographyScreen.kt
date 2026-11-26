package io.github.johankrs.kiri.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.johankrs.kiri.R
import io.github.johankrs.kiri.core.look.LabelLook
import io.github.johankrs.kiri.core.look.LabelWeight

/**
 * How the names of apps are written: the size and the weight of their letters. [preview] shows
 * real apps with their names, which follow every change as soon as it is saved.
 */
@Composable
fun TypographyScreen(
    look: LabelLook,
    onChange: (LabelLook) -> Unit,
    onBack: () -> Unit,
    preview: @Composable () -> Unit,
) {
    var draft by remember { mutableStateOf(look) }
    val change: (LabelLook) -> Unit = { next ->
        if (next != draft) {
            draft = next
            onChange(next)
        }
    }
    var weightsOpen by remember { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.settings_typography), onBack = onBack) {
        // A picture for sighted adjustment only: nothing in it should be announced.
        Box(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
        ) { preview() }
        Spacer(Modifier.height(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
            StepperCard(
                label = stringResource(R.string.typography_label_size),
                valueText = levelText(draft.sizeSp, LabelLook.SIZE_SP, fromOne = true),
                canDecrease = draft.sizeSp > LabelLook.SIZE_SP.first,
                canIncrease = draft.sizeSp < LabelLook.SIZE_SP.last,
                onDecrease = { change(draft.withSize(draft.sizeSp - 1)) },
                onIncrease = { change(draft.withSize(draft.sizeSp + 1)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val weights = LabelWeight.entries
            ChoiceCard(
                label = stringResource(R.string.typography_label_weight),
                valueText = stringResource(draft.weight.labelRes()),
                expanded = weightsOpen,
                onToggle = { weightsOpen = !weightsOpen },
                options = weights.map { stringResource(it.labelRes()) },
                selectedIndex = weights.indexOf(draft.weight),
                onSelect = { index ->
                    change(draft.copy(weight = weights[index]))
                    weightsOpen = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun LabelWeight.labelRes(): Int = when (this) {
    LabelWeight.LIGHT -> R.string.typography_weight_light
    LabelWeight.REGULAR -> R.string.typography_weight_regular
    LabelWeight.MEDIUM -> R.string.typography_weight_medium
}
