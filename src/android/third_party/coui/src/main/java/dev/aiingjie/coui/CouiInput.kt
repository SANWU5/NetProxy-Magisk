package dev.aiingjie.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CouiSearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors = Coui.colors
    Box(modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(CouiShapes.search).background(colors.container8).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouiIcon(CouiGlyph.Search, colors.labelSecondary)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) BasicText(hint, style = TextStyle(fontSize = 15.sp, color = colors.labelSecondary))
                BasicTextField(value, onValueChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = colors.label), cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
            }
            if (value.isNotEmpty()) Box(Modifier.clip(CircleShape).couiPressable(role = Role.Button) { onValueChange("") }.padding(6.dp)) {
                CouiIcon(CouiGlyph.Clear, colors.labelTertiary)
            }
        }
    }
}

@Composable
fun CouiInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    error: String? = null,
    numeric: Boolean = false,
) {
    val colors = Coui.colors
    Column(modifier.fillMaxWidth().padding(horizontal = CouiDimens.rowInnerPadding, vertical = 10.dp)) {
        BasicText(label, style = Coui.type.rowSummary.copy(color = colors.labelSecondary))
        Spacer(Modifier.height(4.dp))
        Box(contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && hint.isNotEmpty()) BasicText(hint, style = Coui.type.rowTitle.copy(color = colors.labelTertiary, fontWeight = null))
            BasicTextField(value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                textStyle = Coui.type.rowTitle.copy(color = if (enabled) colors.label else colors.labelTertiary, fontWeight = null),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next))
        }
        if (error != null) BasicText(error, Modifier.padding(top = 4.dp), style = Coui.type.rowSummary.copy(color = colors.error))
    }
}
