package com.fanjv.netproxy.feature.catalog.presentation.nodes.edit.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.aiingjie.coui.Coui
import dev.aiingjie.coui.CouiCard
import dev.aiingjie.coui.CouiDimens

/** 节点编辑页的 COUI 输入框：卡片内的标签与输入区，保留键盘类型、完成动作与多行输入。 */
@Composable
internal fun NodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = Coui.colors
    CouiCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = CouiDimens.rowInnerPadding, vertical = 10.dp)) {
            BasicText(label, style = Coui.type.rowSummary.copy(color = colors.labelSecondary))
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = Coui.type.rowTitle.copy(color = colors.label, fontWeight = null),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                cursorBrush = SolidColor(colors.accent),
            )
        }
    }
}

/** 编辑页共享的单行文本字段。 */
@Composable
internal fun EditorField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onImeDone: () -> Unit
) {
    NodeTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        modifier = Modifier.padding(bottom = 12.dp),
        keyboardActions = KeyboardActions(onDone = { onImeDone() }),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )
}
