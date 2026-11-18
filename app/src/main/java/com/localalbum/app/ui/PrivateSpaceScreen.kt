package com.localalbum.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import com.localalbum.app.domain.PrivatePhoto

data class PrivateSpaceState(
    val hasPin: Boolean,
    val unlocked: Boolean,
    val biometricAvailable: Boolean,
    val photos: List<PrivatePhoto>,
    val libraryPhotos: List<MediaItem>,
    val busy: Boolean,
)

@Composable
fun PrivateSpaceScreen(
    state: PrivateSpaceState,
    imageLoader: ImageLoader,
    onCreatePin: (String) -> Unit,
    onSubmitPin: (String) -> Boolean,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onImport: (List<MediaItem>) -> Unit,
    onExport: (List<Long>) -> Unit,
    onDelete: (List<Long>) -> Unit,
) {
    when {
        !state.hasPin -> PinGate(
            title = "设置 4 位密码",
            body = "密码只保存在这台手机上。忘记后无法找回，也不能从网上找回来。",
            confirm = true,
            biometricAvailable = false,
            onPinReady = onCreatePin,
            onSubmit = { false },
            onUnlock = {},
        )
        !state.unlocked -> PinGate(
            title = "输入密码后查看",
            body = "这些照片只在这个页面里。\n不会出现在相册。",
            confirm = false,
            biometricAvailable = state.biometricAvailable,
            onPinReady = {},
            onSubmit = onSubmitPin,
            onUnlock = onUnlock,
        )
        else -> PrivateLibrary(
            state = state,
            imageLoader = imageLoader,
            onLock = onLock,
            onImport = onImport,
            onExport = onExport,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun PinGate(
    title: String,
    body: String,
    confirm: Boolean,
    biometricAvailable: Boolean,
    onPinReady: (String) -> Unit,
    onSubmit: (String) -> Boolean,
    onUnlock: () -> Unit,
) {
    var stage by remember { mutableStateOf(0) }
    var first by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val biometric = rememberBiometricPrompt(
        onSuccess = onUnlock,
        onFailed = { error = it },
    )
    val heading = if (confirm && stage == 1) "再输入一次" else title
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(heading, color = Ink, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            body,
            color = Quiet,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (index < entry.length) Ink else Line),
                )
            }
        }
        if (error != null) {
            Text(
                error.orEmpty(),
                color = LikeRed,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        PinPad(
            modifier = Modifier
                .padding(top = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            onDigit = { digit ->
                if (entry.length >= 4) return@PinPad
                error = null
                val next = entry + digit
                entry = next
                if (next.length < 4) return@PinPad
                if (confirm) {
                    if (stage == 0) {
                        first = next
                        entry = ""
                        stage = 1
                    } else if (next == first) {
                        onPinReady(next)
                    } else {
                        error = "两次密码不一致，请重新设置"
                        first = ""
                        entry = ""
                        stage = 0
                    }
                } else if (!onSubmit(next)) {
                    error = "密码不对，请重试"
                    entry = ""
                }
            },
            onDelete = {
                if (entry.isNotEmpty()) entry = entry.dropLast(1)
            },
        )
        if (biometricAvailable) {
            Button(
                onClick = biometric,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
            ) {
                Text("使用指纹或面容", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "删除"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Box(Modifier.weight(1f).heightIn(min = 64.dp))
                    } else {
                        Surface(
                            onClick = { if (label == "删除") onDelete() else onDigit(label) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, color = Ink, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateLibrary(
    state: PrivateSpaceState,
    imageLoader: ImageLoader,
    onLock: () -> Unit,
    onImport: (List<MediaItem>) -> Unit,
    onExport: (List<Long>) -> Unit,
    onDelete: (List<Long>) -> Unit,
) {
    var importing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var hint by remember { mutableStateOf<String?>(null) }
    val liveSelection = selected.intersect(state.photos.map { it.id }.toSet())
    if (importing) {
        ImportPicker(
            photos = state.libraryPhotos,
            picked = picked,
            imageLoader = imageLoader,
            busy = state.busy,
            onToggle = { key -> picked = if (key in picked) picked - key else picked + key },
            onCancel = { importing = false },
            onAskImport = {
                val items = state.libraryPhotos.filter { it.key in picked }
                if (items.isEmpty()) {
                    hint = "请先点选要移入的照片"
                } else {
                    importing = false
                    picked = emptySet()
                    onImport(items)
                }
            },
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "点选照片后，再移出或删除。\n不会自动处理。",
            color = Quiet,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (hint != null) {
            Text(hint.orEmpty(), color = LikeRed, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = {
                hint = null
                importing = true
            },
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
        ) {
            Text("移入照片", style = MaterialTheme.typography.labelLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (liveSelection.isEmpty()) hint = "请先点选照片"
                    else onExport(liveSelection.toList())
                },
                enabled = !state.busy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
            ) {
                Text("移出", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = {
                    if (liveSelection.isEmpty()) hint = "请先点选照片"
                    else onDelete(liveSelection.toList())
                },
                enabled = !state.busy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LikeRed, contentColor = Color.White),
            ) {
                Text("删除", style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(
            onClick = onLock,
            shape = RoundedCornerShape(16.dp),
            color = Field,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("锁定", color = Ink, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.photos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有私密照片", color = Ink, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "移入后只在这里查看，相册里不再显示。",
                    color = Quiet,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.photos, key = { it.id }) { photo ->
                    val marked = photo.id in liveSelection
                    Surface(
                        onClick = {
                            hint = null
                            selected = if (marked) selected - photo.id else selected + photo.id
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Field,
                        border = if (marked) BorderStroke(3.dp, Blue) else null,
                        modifier = Modifier.aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Cover(
                                item = com.localalbum.app.domain.MediaItem(
                                    key = "private:${photo.id}",
                                    uri = photo.uri,
                                    kind = com.localalbum.app.domain.MediaKind.PHOTO,
                                    mimeType = photo.mimeType,
                                    displayName = photo.displayName,
                                    dateTakenMillis = photo.dateTakenMillis,
                                    width = 0,
                                    height = 0,
                                    durationMs = 0,
                                    bucketId = "private",
                                    bucketName = "私密空间",
                                ),
                                imageLoader = imageLoader,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            if (marked) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x66007AFF)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("已选", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPicker(
    photos: List<MediaItem>,
    picked: Set<String>,
    imageLoader: ImageLoader,
    busy: Boolean,
    onToggle: (String) -> Unit,
    onCancel: () -> Unit,
    onAskImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("点选要移入的照片", color = Ink, style = MaterialTheme.typography.titleLarge)
        Text(
            "移入前还会再确认一次。确认后，手机会让你从相册里删除原来的照片。",
            color = Quiet,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (photos.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "相册里没有可以移入的照片",
                    color = Quiet,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(photos, key = { it.key }) { photo ->
                    val marked = photo.key in picked
                    Surface(
                        onClick = { onToggle(photo.key) },
                        shape = RoundedCornerShape(12.dp),
                        color = Field,
                        border = if (marked) BorderStroke(3.dp, Blue) else null,
                        modifier = Modifier.aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Cover(
                                item = photo,
                                imageLoader = imageLoader,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            )
                            if (marked) {
                                Box(
                                    Modifier.fillMaxSize().background(Color(0x66007AFF)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("移入", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onAskImport,
            enabled = !busy && photos.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("移入所选", style = MaterialTheme.typography.labelLarge)
        }
        Surface(
            onClick = onCancel,
            shape = RoundedCornerShape(16.dp),
            color = Field,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("返回", color = Blue, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
