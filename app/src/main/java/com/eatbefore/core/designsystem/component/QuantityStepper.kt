package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.core.designsystem.theme.Dimens

/**
 * Minus and plus around whatever shows the amount.
 *
 * Almost every amount in a household inventory is one, two or three, and reaching those
 * through a keyboard or a dialog costs more taps than the change is worth. The exact
 * value stays editable next to this — the stepper is for the common case, not the only one.
 */
@Composable
fun QuantityStepper(
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        IconButton(onClick = onDecrease, enabled = decreaseEnabled) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.quantity_decrease),
            )
        }
        content()
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quantity_increase))
        }
    }
}

/** Convenience overload showing the amount as plain text. */
@Composable
fun QuantityStepper(
    text: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseEnabled: Boolean = true,
) {
    QuantityStepper(
        onDecrease = onDecrease,
        onIncrease = onIncrease,
        modifier = modifier,
        decreaseEnabled = decreaseEnabled,
    ) {
        Text(text)
    }
}
