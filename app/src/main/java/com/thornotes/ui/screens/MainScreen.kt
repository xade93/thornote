package com.thornotes.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thornotes.analysis.EnglishDictionaryLookup
import com.thornotes.capture.ScreenCaptureManager
import com.thornotes.capture.ScreenCaptureService
import com.thornotes.data.NotebookRepository
import com.thornotes.data.models.AppSettings
import com.thornotes.data.models.CaptureState
import com.thornotes.data.models.EnglishDictionaryEntry
import com.thornotes.data.models.NotebookEntry
import com.thornotes.data.models.NotebookEntryType
import com.thornotes.data.models.NotebookPageInfo
import com.thornotes.ocr.TextRecognizer
import com.thornotes.ui.components.DictionaryResultView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NotebookPage {
    NOTEBOOK,
    DICTIONARY,
}

private enum class PendingCapture {
    NONE,
    SCREENSHOT,
    REGION_OCR,
    CROP,
}

private val StarBorderColor = Color(0xFFFFC107)
private val PinBorderColor = Color(0xFF4DD0E1)
private const val DoubleTapWindowMillis = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    captureManager: ScreenCaptureManager,
    textRecognizer: TextRecognizer,
    dictionary: EnglishDictionaryLookup,
    settings: AppSettings,
    notebook: NotebookRepository,
    captureState: CaptureState,
    onCaptureStateChange: (CaptureState) -> Unit,
    onSettingsClick: () -> Unit,
    onRestoreGameFocus: () -> Unit = {},
    onCropClick: (Bitmap) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textSize by settings.textSize.collectAsState()
    val cropEnabled by settings.cropEnabled.collectAsState()
    val ocrLanguage by settings.ocrLanguage.collectAsState()
    val pages by notebook.pages.collectAsState()
    val currentPageId by notebook.currentPageId.collectAsState()
    val entries by notebook.entries.collectAsState()
    val currentNotebookPage = pages.firstOrNull { it.id == currentPageId }

    var currentPage by remember { mutableStateOf(NotebookPage.NOTEBOOK) }
    var dictionaryResult by remember { mutableStateOf<List<EnglishDictionaryEntry>>(emptyList()) }
    var dictionaryInput by remember { mutableStateOf("") }
    var pendingCapture by remember { mutableStateOf(PendingCapture.NONE) }
    var screenBlackout by remember { mutableStateOf(false) }
    val isProcessing = captureState is CaptureState.Capturing || captureState is CaptureState.Processing

    LaunchedEffect(captureState) {
        val error = captureState as? CaptureState.Error ?: return@LaunchedEffect
        Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
        onCaptureStateChange(CaptureState.Idle)
    }

    fun cropBitmap(bitmap: Bitmap): Bitmap {
        if (!cropEnabled) return bitmap
        val region = settings.cropRegion
        val x = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
        val y = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
        val w = ((region.right - region.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val h = ((region.bottom - region.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    fun captureScreenshotEntry() {
        scope.launch {
            onCaptureStateChange(CaptureState.Capturing)
            val bitmap = captureManager.captureScreen()
            if (bitmap == null) {
                onCaptureStateChange(CaptureState.Error("Failed to capture screen"))
                onRestoreGameFocus()
                return@launch
            }
            notebook.addScreenshot(bitmap)
            onCaptureStateChange(CaptureState.Idle)
            onRestoreGameFocus()
        }
    }

    fun captureRegionOcrEntry() {
        scope.launch {
            onCaptureStateChange(CaptureState.Capturing)
            val fullBitmap = captureManager.captureScreen()
            if (fullBitmap == null) {
                onCaptureStateChange(CaptureState.Error("Failed to capture screen"))
                onRestoreGameFocus()
                return@launch
            }
            onRestoreGameFocus()
            onCaptureStateChange(CaptureState.Processing)
            val text = textRecognizer.recognizeText(cropBitmap(fullBitmap), ocrLanguage)
            if (text.isNullOrBlank()) {
                onCaptureStateChange(CaptureState.Error("No text found in selected region"))
                return@launch
            }
            notebook.addOcrText(text)
            onCaptureStateChange(CaptureState.Idle)
        }
    }

    fun openCropSelector() {
        scope.launch {
            val bitmap = captureManager.captureScreen()
            if (bitmap != null) onCropClick(bitmap)
        }
    }

    fun runPendingCapture() {
        val action = pendingCapture
        when (action) {
            PendingCapture.SCREENSHOT -> captureScreenshotEntry()
            PendingCapture.REGION_OCR -> captureRegionOcrEntry()
            PendingCapture.CROP -> openCropSelector()
            PendingCapture.NONE -> Unit
        }
        pendingCapture = PendingCapture.NONE
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.captureManager = captureManager
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            captureManager.awaitProjectionReady { runPendingCapture() }
        } else {
            pendingCapture = PendingCapture.NONE
            onCaptureStateChange(CaptureState.Error("Permission denied"))
        }
    }

    fun requestCapture(action: PendingCapture) {
        pendingCapture = action
        if (captureManager.isReady) {
            runPendingCapture()
        } else {
            val intent = captureManager.projectionManager.createScreenCaptureIntent()
            projectionLauncher.launch(intent)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    NotebookTopBarTitle(
                        pages = pages,
                        currentNotebookPage = currentNotebookPage,
                        onNotebookPageSelected = notebook::selectPage,
                        onCreateNotebookPage = notebook::createPage,
                        onRenameNotebookPage = notebook::renamePage,
                        onDeleteNotebookPage = notebook::deletePage,
                        onSettingsClick = onSettingsClick,
                        onTimeDoubleTap = { screenBlackout = true },
                    )
                }
            },
            bottomBar = {
                if (currentPage == NotebookPage.NOTEBOOK) {
                    CompactCaptureBar(
                        cropEnabled = cropEnabled,
                        isProcessing = isProcessing,
                        onScreenshotClick = { requestCapture(PendingCapture.SCREENSHOT) },
                        onOcrClick = {
                            if (cropEnabled) {
                                requestCapture(PendingCapture.REGION_OCR)
                            } else {
                                requestCapture(PendingCapture.CROP)
                            }
                        },
                        onEditRegionClick = { requestCapture(PendingCapture.CROP) },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (currentPage) {
                    NotebookPage.NOTEBOOK -> NotebookPageContent(
                        entries = entries,
                        textSize = textSize,
                        imagePathResolver = notebook::resolveImagePath,
                        onTextChange = notebook::updateText,
                        onToggleStar = notebook::toggleStar,
                        onTogglePin = notebook::togglePin,
                        onDeleteEntry = notebook::deleteEntry,
                        modifier = Modifier.weight(1f),
                    )
                    NotebookPage.DICTIONARY -> DictionaryPageContent(
                        input = dictionaryInput,
                        result = dictionaryResult,
                        textSize = textSize,
                        onInputChange = {
                            dictionaryInput = it
                            if (it.isBlank()) {
                                dictionaryResult = emptyList()
                                onCaptureStateChange(CaptureState.Idle)
                            }
                        },
                        onLookupClick = {
                            val text = dictionaryInput.trim()
                            if (text.isBlank()) {
                                onCaptureStateChange(CaptureState.Error("Enter text to look up"))
                            } else {
                                dictionaryResult = dictionary.lookup(text)
                                onCaptureStateChange(CaptureState.Idle)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (screenBlackout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { screenBlackout = false })
                    },
            )
        }
    }
}

@Composable
private fun NotebookTopBarTitle(
    pages: List<NotebookPageInfo>,
    currentNotebookPage: NotebookPageInfo?,
    onNotebookPageSelected: (String) -> Unit,
    onCreateNotebookPage: (String) -> Unit,
    onRenameNotebookPage: (String, String) -> Unit,
    onDeleteNotebookPage: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onTimeDoubleTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeStatus(onDoubleTap = onTimeDoubleTap)

        NotebookPageSelector(
            pages = pages,
            currentPage = currentNotebookPage,
            onPageSelected = onNotebookPageSelected,
            onCreatePage = onCreateNotebookPage,
            onRenamePage = onRenameNotebookPage,
            onDeletePage = onDeleteNotebookPage,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.weight(1f),
        )

        BatteryStatus()
    }
}

private data class BatterySnapshot(
    val level: Int = 100,
    val charging: Boolean = false,
)

@Composable
private fun TimeStatus(onDoubleTap: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var timeText by remember { mutableStateOf(timeFormat.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            timeText = timeFormat.format(Date())
            delay(15_000)
        }
    }

    Box(
        modifier = Modifier
            .width(68.dp)
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = timeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun BatteryStatus() {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(readBatterySnapshot(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                battery = intent.toBatterySnapshot()
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        battery = sticky.toBatterySnapshot()
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Row(
        modifier = Modifier
            .width(44.dp)
            .height(36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${battery.level}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private fun readBatterySnapshot(context: Context): BatterySnapshot {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return intent.toBatterySnapshot()
}

private fun Intent?.toBatterySnapshot(): BatterySnapshot {
    if (this == null) return BatterySnapshot()
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val percent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
        100
    }
    return BatterySnapshot(
        level = percent,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL,
    )
}

@Composable
private fun TopModeSwitch(
    currentPage: NotebookPage,
    onPageChange: (NotebookPage) -> Unit,
) {
    val togglePage = {
        onPageChange(
            if (currentPage == NotebookPage.NOTEBOOK) {
                NotebookPage.DICTIONARY
            } else {
                NotebookPage.NOTEBOOK
            }
        )
    }

    Row(
        modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ModeIconOption(
            label = "\u25a4",
            selected = currentPage == NotebookPage.NOTEBOOK,
            onClick = togglePage,
            modifier = Modifier.weight(1f),
        )
        ModeIconOption(
            label = "Aa",
            selected = currentPage == NotebookPage.DICTIONARY,
            onClick = togglePage,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeIconOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotebookPageContent(
    entries: List<NotebookEntry>,
    textSize: Int,
    imagePathResolver: (String?) -> String?,
    onTextChange: (String, String) -> Unit,
    onToggleStar: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val firstVisibleEntryId by remember(entries, listState) {
        derivedStateOf { entries.getOrNull(listState.firstVisibleItemIndex)?.id }
    }
    var selectedPreviewId by remember(entries.firstOrNull()?.pageId) { mutableStateOf<String?>(null) }
    var pendingScrollEntryId by remember(entries.firstOrNull()?.pageId) { mutableStateOf<String?>(null) }
    val highlightedEntryId = selectedPreviewId ?: firstVisibleEntryId
    val screenshotContentOffset = with(LocalDensity.current) { 40.dp.toPx().toInt() }
    var entryPendingDelete by remember { mutableStateOf<NotebookEntry?>(null) }

    LaunchedEffect(entries.firstOrNull()?.id) {
        val newestEntry = entries.firstOrNull() ?: return@LaunchedEffect
        selectedPreviewId = newestEntry.id
        pendingScrollEntryId = null
        listState.scrollToItem(0)
    }

    LaunchedEffect(pendingScrollEntryId, entries) {
        val entryId = pendingScrollEntryId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.id == entryId }
        if (index < 0) {
            pendingScrollEntryId = null
            return@LaunchedEffect
        }

        val entry = entries[index]
        val scrollOffset = if (entry.type == NotebookEntryType.SCREENSHOT) {
            screenshotContentOffset
        } else {
            0
        }
        listState.scrollToItem(index, scrollOffset)
        pendingScrollEntryId = null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entries.size > 1) {
            EntryMinimap(
                entries = entries,
                selectedEntryId = highlightedEntryId,
                imagePathResolver = imagePathResolver,
                onEntrySelected = { entry ->
                    selectedPreviewId = entry.id
                    pendingScrollEntryId = entry.id
                },
                onToggleStar = onToggleStar,
                onTogglePin = onTogglePin,
            )
        }

        if (entries.isEmpty()) {
            EmptyState(
                text = "Capture screenshots or OCR text to build this notebook.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.id },
                ) { _, entry ->
                    NotebookEntryView(
                        entry = entry,
                        textSize = textSize,
                        imagePathResolver = imagePathResolver,
                        onTextChange = onTextChange,
                        onToggleStar = onToggleStar,
                        onTogglePin = onTogglePin,
                        onDelete = {
                            entryPendingDelete = entry
                        },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    entryPendingDelete?.let { entry ->
        val isScreenshot = entry.type == NotebookEntryType.SCREENSHOT
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text(if (isScreenshot) "Delete photo?" else "Delete text block?") },
            text = {
                Text(
                    text = if (isScreenshot) {
                        "This will remove the photo from this notebook page."
                    } else {
                        "This will remove this text block from this notebook page."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedPreviewId == entry.id) selectedPreviewId = null
                        onDeleteEntry(entry.id)
                        entryPendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EntryMinimap(
    entries: List<NotebookEntry>,
    selectedEntryId: String?,
    imagePathResolver: (String?) -> String?,
    onEntrySelected: (NotebookEntry) -> Unit,
    onToggleStar: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    val railState = rememberLazyListState()
    var lastTapEntryId by remember { mutableStateOf<String?>(null) }
    var lastTapAt by remember { mutableStateOf(0L) }
    val railEntries = remember(entries) {
        val pinned = entries.filter { it.type == NotebookEntryType.SCREENSHOT && it.isPinned }
        val pinnedIds = pinned.mapTo(mutableSetOf()) { it.id }
        pinned + entries.filterNot { it.id in pinnedIds }
    }

    LaunchedEffect(railEntries.firstOrNull()?.id, railEntries.count { it.isPinned }) {
        if (railEntries.isNotEmpty()) {
            railState.animateScrollToItem(0)
        }
    }

    LazyRow(
        state = railState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = railEntries,
            key = { _, entry -> entry.id },
        ) { index, entry ->
            val selected = entry.id == selectedEntryId
            val originalIndex = entries.indexOfFirst { it.id == entry.id }.takeIf { it >= 0 } ?: index
            val resolvedImagePath = imagePathResolver(entry.imagePath)
            val thumbnail = remember(resolvedImagePath) {
                resolvedImagePath?.let { decodeThumbnail(it, maxSize = 180) }
            }
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = when {
                            entry.isPinned -> 3.dp
                            entry.isStarred -> 3.dp
                            selected -> 2.dp
                            else -> 1.dp
                        },
                        color = when {
                            entry.isPinned -> PinBorderColor
                            entry.isStarred -> StarBorderColor
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    .pointerInput(entry.id, entry.type) {
                        detectTapGestures(
                            onTap = {
                                val now = SystemClock.uptimeMillis()
                                val isDoubleTap = lastTapEntryId == entry.id &&
                                    now - lastTapAt <= DoubleTapWindowMillis
                                if (entry.type == NotebookEntryType.SCREENSHOT && isDoubleTap) {
                                    onTogglePin(entry.id)
                                    lastTapEntryId = null
                                    lastTapAt = 0L
                                } else {
                                    onEntrySelected(entry)
                                    lastTapEntryId = entry.id
                                    lastTapAt = now
                                }
                            },
                            onLongPress = {
                                if (entry.type == NotebookEntryType.SCREENSHOT) {
                                    onToggleStar(entry.id)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (entry.type) {
                    NotebookEntryType.SCREENSHOT -> {
                        if (thumbnail != null) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = "Jump to screenshot ${originalIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    NotebookEntryType.OCR_TEXT -> OcrMinimapItem(
                        index = originalIndex,
                        text = entry.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrMinimapItem(
    index: Int,
    text: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "OCR ${index + 1}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = text.trim().ifBlank { "Text" },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DictionaryPageContent(
    input: String,
    result: List<EnglishDictionaryEntry>,
    textSize: Int,
    onInputChange: (String) -> Unit,
    onLookupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Enter English word") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        CompactActionButton(
            label = "Look Up Text",
            enabled = true,
            onClick = onLookupClick,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (result.isEmpty()) {
                EmptyState("Type an English word to look up definitions and synonyms.")
            } else {
                Text(
                    text = input.trim(),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                DictionaryResultView(result = result, textSize = textSize)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NotebookPageSelector(
    pages: List<NotebookPageInfo>,
    currentPage: NotebookPageInfo?,
    onPageSelected: (String) -> Unit,
    onCreatePage: (String) -> Unit,
    onRenamePage: (String, String) -> Unit,
    onDeletePage: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageMenuExpanded by remember { mutableStateOf(false) }
    var creatingPage by remember { mutableStateOf(false) }
    var renamingPage by remember { mutableStateOf(false) }
    var pagePendingDelete by remember { mutableStateOf<NotebookPageInfo?>(null) }
    var newPageName by remember { mutableStateOf("") }
    var renamedPageName by remember(currentPage?.id) { mutableStateOf(currentPage?.name.orEmpty()) }
    val pageName = currentPage?.name ?: "No Page"
    val pageStats = currentPage?.let { "${it.entryCount} entries - ${formatBytes(it.sizeBytes)}" } ?: ""

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { pageMenuExpanded = true }
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pageName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pageStats.isNotEmpty()) {
                Text(
                    text = pageStats,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = pageMenuExpanded,
            onDismissRequest = { pageMenuExpanded = false },
        ) {
            pages.forEach { page ->
                DropdownMenuItem(
                    text = { Text("${page.name} (${formatBytes(page.sizeBytes)})") },
                    onClick = {
                        onPageSelected(page.id)
                        pageMenuExpanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("New page") },
                onClick = {
                    pageMenuExpanded = false
                    creatingPage = true
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    pageMenuExpanded = false
                    onSettingsClick()
                },
            )
            if (currentPage != null) {
                DropdownMenuItem(
                    text = { Text("Rename current page") },
                    onClick = {
                        renamedPageName = currentPage.name
                        pageMenuExpanded = false
                        renamingPage = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete current page") },
                    onClick = {
                        pageMenuExpanded = false
                        pagePendingDelete = currentPage
                    },
                )
            }
        }
    }

    if (creatingPage) {
        AlertDialog(
            onDismissRequest = { creatingPage = false },
            title = { Text("New page") },
            text = {
                OutlinedTextField(
                    value = newPageName,
                    onValueChange = { newPageName = it },
                    singleLine = true,
                    placeholder = { Text("Notebook page name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePage(newPageName)
                        newPageName = ""
                        creatingPage = false
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { creatingPage = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (renamingPage && currentPage != null) {
        AlertDialog(
            onDismissRequest = { renamingPage = false },
            title = { Text("Rename page") },
            text = {
                OutlinedTextField(
                    value = renamedPageName,
                    onValueChange = { renamedPageName = it },
                    singleLine = true,
                    placeholder = { Text("Notebook page name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenamePage(currentPage.id, renamedPageName)
                        renamingPage = false
                    },
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingPage = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    pagePendingDelete?.let { page ->
        AlertDialog(
            onDismissRequest = { pagePendingDelete = null },
            title = { Text("Delete notebook page?") },
            text = {
                Text("This will delete \"${page.name}\" and all photos and text blocks on that page.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePage(page.id)
                        pagePendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pagePendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CompactCaptureBar(
    cropEnabled: Boolean,
    isProcessing: Boolean,
    onScreenshotClick: () -> Unit,
    onOcrClick: () -> Unit,
    onEditRegionClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactActionButton(
            label = "Shot",
            enabled = !isProcessing,
            onClick = onScreenshotClick,
            modifier = Modifier.weight(1f),
        )
        CompactActionButton(
            label = if (cropEnabled) "OCR" else "Set Region",
            enabled = !isProcessing,
            onClick = onOcrClick,
            onLongClick = onEditRegionClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val background = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .pointerInput(enabled, onLongClick) {
                detectTapGestures(
                    onTap = {
                        if (enabled) onClick()
                    },
                    onLongPress = {
                        if (enabled) (onLongClick ?: onClick)()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}

@Composable
private fun NotebookEntryView(
    entry: NotebookEntry,
    textSize: Int,
    imagePathResolver: (String?) -> String?,
    onTextChange: (String, String) -> Unit,
    onToggleStar: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val time = remember(entry.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.createdAt))
    }
    val bodySize = when (textSize) {
        AppSettings.TEXT_SIZE_SMALL -> 14.sp
        AppSettings.TEXT_SIZE_LARGE -> 20.sp
        else -> 16.sp
    }
    var dragAmount by remember(entry.id) { mutableStateOf(0f) }
    var lastImageTapAt by remember(entry.id) { mutableStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(entry.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        dragAmount += drag
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(dragAmount) > 160f) {
                            onDelete(entry.id)
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f },
                )
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (entry.type == NotebookEntryType.SCREENSHOT) "Screenshot - $time" else "OCR Text - $time",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        when (entry.type) {
            NotebookEntryType.SCREENSHOT -> {
                val resolvedImagePath = imagePathResolver(entry.imagePath)
                val bitmap = remember(resolvedImagePath) {
                    resolvedImagePath?.let { BitmapFactory.decodeFile(it)?.trimHorizontalBlackBars() }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (entry.isPinned || entry.isStarred) 3.dp else 0.dp,
                                color = when {
                                    entry.isPinned -> PinBorderColor
                                    entry.isStarred -> StarBorderColor
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .pointerInput(entry.id) {
                                detectTapGestures(
                                    onTap = {
                                        val now = SystemClock.uptimeMillis()
                                        if (now - lastImageTapAt <= DoubleTapWindowMillis) {
                                            onTogglePin(entry.id)
                                            lastImageTapAt = 0L
                                        } else {
                                            lastImageTapAt = now
                                        }
                                    },
                                    onLongPress = { onToggleStar(entry.id) },
                                )
                            },
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    Text(
                        text = "Screenshot file is missing",
                        fontSize = bodySize,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            NotebookEntryType.OCR_TEXT -> {
                OutlinedTextField(
                    value = entry.text,
                    onValueChange = { onTextChange(entry.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = bodySize),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
    }
}

private fun decodeThumbnail(path: String, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (largestSide / sampleSize > maxSize) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )?.trimHorizontalBlackBars()
}

private fun Bitmap.trimHorizontalBlackBars(): Bitmap {
    if (width <= 1 || height <= 1) return this

    val top = firstContentRow()
    val bottom = lastContentRow()
    if (top == 0 && bottom == height - 1) return this
    if (bottom <= top) return this

    return Bitmap.createBitmap(this, 0, top, width, bottom - top + 1)
}

private fun Bitmap.firstContentRow(): Int {
    for (y in 0 until height) {
        if (!isBlackBarRow(y)) return y
    }
    return 0
}

private fun Bitmap.lastContentRow(): Int {
    for (y in height - 1 downTo 0) {
        if (!isBlackBarRow(y)) return y
    }
    return height - 1
}

private fun Bitmap.isBlackBarRow(y: Int): Boolean {
    val step = maxOf(1, width / 160)
    var samples = 0
    var blackSamples = 0
    var x = 0
    while (x < width) {
        val pixel = getPixel(x, y)
        val red = (pixel shr 16) and 0xff
        val green = (pixel shr 8) and 0xff
        val blue = pixel and 0xff
        if (red + green + blue <= 54) {
            blackSamples++
        }
        samples++
        x += step
    }
    return samples > 0 && blackSamples.toFloat() / samples >= 0.98f
}
