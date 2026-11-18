package com.localalbum.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.localalbum.app.domain.MediaItem
import com.localalbum.app.domain.SimilarGroup
import java.time.ZoneId

@Composable
fun SimilarScreen(
    groups: List<SimilarGroup>,
    scanning: Boolean,
    libraryLoading: Boolean,
    imageLoader: ImageLoader,
    busy: Boolean,
    onAskDelete: (List<MediaItem>) -> Unit,
    initialSelected: Set<String> = emptySet(),
) {
    var selected by remember { mutableStateOf(initialSelected) }
    val zone = remember { ZoneId.systemDefault() }
    when {
        libraryLoading || scanning -> CenterMessage(
            title = "正在查找相似照片",
            body = "只在这台手机上比较画面，不会上传。",
            loading = true,
        )
        groups.isEmpty() -> CenterMessage(
            title = "没有发现相似照片",
            body = "连拍、重复保存或几乎一样的照片，会在这里分成一组，方便你挑选后删除。",
        )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "点选要删除的照片，再点下面的按钮。\n没点选的会留下，不会自动删除。",
                    color = Quiet,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            items(groups, key = { it.id }) { group ->
                val chosen = group.items.filter { it.key in selected }
                SimilarGroupCard(
                    group = group,
                    zone = zone,
                    selected = selected,
                    imageLoader = imageLoader,
                    busy = busy,
                    onToggle = { key ->
                        selected = if (key in selected) selected - key else selected + key
                    },
                    onAskDelete = { if (chosen.isNotEmpty()) onAskDelete(chosen) },
                )
            }
        }
    }
}

@Composable
private fun SimilarGroupCard(
    group: SimilarGroup,
    zone: ZoneId,
    selected: Set<String>,
    imageLoader: ImageLoader,
    busy: Boolean,
    onToggle: (String) -> Unit,
    onAskDelete: () -> Unit,
) {
    val chosen = group.items.count { it.key in selected }
    val newest = group.items.firstOrNull()?.dateTakenMillis
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${group.items.size} 张相似",
                color = Ink,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                com.localalbum.app.domain.ChineseDates.captureDate(newest, zone),
                color = Quiet,
                style = MaterialTheme.typography.bodyMedium,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.items, key = { it.key }) { item ->
                    val marked = item.key in selected
                    Surface(
                        onClick = { onToggle(item.key) },
                        shape = RoundedCornerShape(12.dp),
                        color = Field,
                        border = if (marked) BorderStroke(3.dp, LikeRed) else null,
                        modifier = Modifier.size(104.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Cover(
                                item = item,
                                imageLoader = imageLoader,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            if (marked) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x66FF3B30)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "删除",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onAskDelete,
                enabled = chosen > 0 && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LikeRed,
                    contentColor = Color.White,
                    disabledContainerColor = Field,
                    disabledContentColor = Quiet,
                ),
            ) {
                Text(
                    if (chosen > 0) "删除所选 $chosen 张" else "先点选要删除的照片",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}
