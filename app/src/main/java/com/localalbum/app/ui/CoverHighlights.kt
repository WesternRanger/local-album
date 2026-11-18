package com.localalbum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localalbum.app.domain.AlbumHighlights

@Composable
fun CoverHighlights(
    highlights: AlbumHighlights,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!highlights.hasContent) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (large) 8.dp else 4.dp),
    ) {
        if (highlights.emoji.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                highlights.emoji.forEach { symbol ->
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = if (large) 32.sp else 26.sp,
                            lineHeight = if (large) 40.sp else 34.sp,
                            letterSpacing = 0.sp,
                            color = Color.Unspecified,
                        ),
                    )
                }
            }
        }
        if (highlights.thoughts.isNotEmpty()) {
            Text(
                text = "热门感想",
                color = Blue,
                style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
            highlights.thoughts.forEach { thought ->
                Text(
                    text = thought,
                    color = Ink,
                    style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = if (large) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
