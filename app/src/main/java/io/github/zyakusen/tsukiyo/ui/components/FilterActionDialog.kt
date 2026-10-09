package io.github.zyakusen.tsukiyo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 长按标签后的操作弹窗：正选（仅含此项）/ 反选（排除此项），外加可选的「收藏 / 取消收藏」。
 *
 * 四个按钮的顺序（左 → 右）：**取消、收藏、反选、正选**
 *  * 两个筛选动作是最常用的主操作，保持原来"反选在左、正选在右"的相对顺序并放在最右；
 *  * 收藏是次要动作，夹在中间且文案随状态变化（收藏 / 取消收藏）；
 *  * 取消按 Material 惯例放最左（否定操作在前）。
 *
 * @param isFavorite 传 null 表示**不显示**收藏按钮（收藏页里的长按不需要它）
 */
@Composable
fun FilterActionDialog(
    title: String,
    onDismiss: () -> Unit,
    onInclude: () -> Unit,
    onExclude: () -> Unit,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选：$title") },
        text = { Text("选择正选（仅含此项）还是反选（排除此项）") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                if (isFavorite != null && onToggleFavorite != null) {
                    TextButton(onClick = onToggleFavorite) { Text(if (isFavorite) "取消收藏" else "收藏") }
                }
                TextButton(onClick = onExclude) { Text("反选") }
                TextButton(onClick = onInclude) { Text("正选") }
            }
        }
    )
}
