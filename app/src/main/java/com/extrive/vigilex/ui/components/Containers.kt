package com.extrive.vigilex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.ui.theme.BackgroundWhite
import com.extrive.vigilex.ui.theme.BorderSubtle
import com.extrive.vigilex.ui.theme.DividerColor

/** White, lightly bordered container for grouped list rows. */
@Composable
fun ListContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundWhite)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        content = content
    )
}

/** Divider for rows inside a ListContainer, inset to align with row content. */
@Composable
fun RowDivider(startIndent: androidx.compose.ui.unit.Dp = 16.dp) {
    HorizontalDivider(
        color = DividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(start = startIndent)
    )
}

/** Pinned bottom area for a screen's primary action. */
@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundWhite)
    ) {
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            content = content
        )
    }
}
