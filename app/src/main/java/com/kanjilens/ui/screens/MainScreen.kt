package com.kanjilens.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kanjilens.analysis.EnglishDictionaryLookup
import com.kanjilens.capture.ScreenCaptureManager
import com.kanjilens.capture.ScreenCaptureService
import com.kanjilens.data.NotebookRepository
import com.kanjilens.data.models.AppSettings
import com.kanjilens.data.models.CaptureState
import com.kanjilens.data.models.EnglishDictionaryEntry
import com.kanjilens.data.models.NotebookEntry
import com.kanjilens.data.models.NotebookEntryType
import com.kanjilens.data.models.NotebookPageInfo
import com.kanjilens.ocr.TextRecognizer
import com.kanjilens.ui.components.DictionaryResultView
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
    onCropClick: (Bitmap) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textSize by settings.textSize.collectAsState()
    val cropEnabled by settings.cropEnabled.collectAsState()
    val pages by notebook.pages.collectAsState()
    val currentPageId by notebook.currentPageId.collectAsState()
    val entries by notebook.entries.collectAsState()
    val currentNotebookPage = pages.firstOrNull { it.id == currentPageId }

    var currentPage by remember { mutableStateOf(NotebookPage.NOTEBOOK) }
    var dictionaryResult by remember { mutableStateOf<List<EnglishDictionaryEntry>>(emptyList()) }
    var dictionaryInput by remember { mutableStateOf("") }
    var pendingCapture by remember { mutableStateOf(PendingCapture.NONE) }
    val isProcessing = captureState is CaptureState.Capturing || captureState is CaptureState.Processing

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
                return@launch
            }
            notebook.addScreenshot(bitmap)
            onCaptureStateChange(CaptureState.Idle)
        }
    }

    fun captureRegionOcrEntry() {
        scope.launch {
            onCaptureStateChange(CaptureState.Capturing)
            val fullBitmap = captureManager.captureScreen()
            if (fullBitmap == null) {
                onCaptureStateChange(CaptureState.Error("Failed to capture screen"))
                return@launch
            }
            onCaptureStateChange(CaptureState.Processing)
            val text = textRecognizer.recognizeText(cropBitmap(fullBitmap))
            if (text.isNullOrBlank()) {
                onCaptureStateChange(CaptureState.Error("No Japanese text found in selected region"))
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
        when (pendingCapture) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TopPageSwitch(
                        currentPage = currentPage,
                        onPageChange = {
                            currentPage = it
                            onCaptureStateChange(CaptureState.Idle)
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Text(
                            text = "\u2699",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
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
                    pages = pages,
                    currentPage = currentNotebookPage,
                    entries = entries,
                    state = captureState,
                    cropEnabled = cropEnabled,
                    textSize = textSize,
                    onPageSelected = notebook::selectPage,
                    onCreatePage = notebook::createPage,
                    onDeletePage = notebook::deletePage,
                    onTextChange = notebook::updateText,
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
}

@Composable
private fun TopPageSwitch(
    currentPage: NotebookPage,
    onPageChange: (NotebookPage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        PageOption(
            label = "Notebook",
            selected = currentPage == NotebookPage.NOTEBOOK,
            onClick = { onPageChange(NotebookPage.NOTEBOOK) },
            modifier = Modifier.weight(1f),
        )
        PageOption(
            label = "Dictionary",
            selected = currentPage == NotebookPage.DICTIONARY,
            onClick = { onPageChange(NotebookPage.DICTIONARY) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
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
    pages: List<NotebookPageInfo>,
    currentPage: NotebookPageInfo?,
    entries: List<NotebookEntry>,
    state: CaptureState,
    cropEnabled: Boolean,
    textSize: Int,
    onPageSelected: (String) -> Unit,
    onCreatePage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onTextChange: (String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val screenshotEntries = remember(entries) {
        entries.filter { it.type == NotebookEntryType.SCREENSHOT && it.imagePath != null }
    }
    val firstVisibleEntryId by remember {
        derivedStateOf { entries.getOrNull(listState.firstVisibleItemIndex)?.id }
    }
    var selectedPreviewId by remember(currentPage?.id) { mutableStateOf<String?>(null) }
    val highlightedEntryId = selectedPreviewId ?: firstVisibleEntryId
    val scope = rememberCoroutineScope()
    val screenshotContentOffset = with(LocalDensity.current) { 40.dp.toPx().toInt() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NotebookPageHeader(
            pages = pages,
            currentPage = currentPage,
            onPageSelected = onPageSelected,
            onCreatePage = onCreatePage,
            onDeletePage = onDeletePage,
        )

        StatusLine(state = state)

        if (screenshotEntries.size > 1) {
            ScreenshotMinimap(
                screenshots = screenshotEntries,
                selectedEntryId = highlightedEntryId,
                onScreenshotSelected = { entry ->
                    val index = entries.indexOfFirst { it.id == entry.id }
                    if (index >= 0) {
                        selectedPreviewId = entry.id
                        scope.launch { listState.animateScrollToItem(index, screenshotContentOffset) }
                    }
                },
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
                        onTextChange = onTextChange,
                        onDelete = {
                            if (selectedPreviewId == it) selectedPreviewId = null
                            onDeleteEntry(it)
                        },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ScreenshotMinimap(
    screenshots: List<NotebookEntry>,
    selectedEntryId: String?,
    onScreenshotSelected: (NotebookEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Screenshots",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = screenshots,
                key = { _, entry -> entry.id },
            ) { index, entry ->
                val selected = entry.id == selectedEntryId
                val thumbnail = remember(entry.imagePath) {
                    entry.imagePath?.let { decodeThumbnail(it, maxSize = 180) }
                }
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onScreenshotSelected(entry) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = "Jump to screenshot ${index + 1}",
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
            }
        }
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
private fun NotebookPageHeader(
    pages: List<NotebookPageInfo>,
    currentPage: NotebookPageInfo?,
    onPageSelected: (String) -> Unit,
    onCreatePage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
) {
    var pageMenuExpanded by remember { mutableStateOf(false) }
    var creatingPage by remember { mutableStateOf(false) }
    var newPageName by remember { mutableStateOf("") }
    val pageName = currentPage?.name ?: "No Page"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = pageName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { pageMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
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
                    if (currentPage != null) {
                        DropdownMenuItem(
                            text = { Text("Delete current page") },
                            onClick = {
                                onDeletePage(currentPage.id)
                                pageMenuExpanded = false
                            },
                        )
                    }
                }
            }
            SmallTextButton(
                label = "+",
                onClick = { creatingPage = !creatingPage },
            )
        }

        currentPage?.let { page ->
            Text(
                text = "${page.entryCount} entries - ${formatBytes(page.sizeBytes)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (creatingPage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newPageName,
                    onValueChange = { newPageName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Novel / game name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                SmallTextButton(
                    label = "Create",
                    onClick = {
                        onCreatePage(newPageName)
                        newPageName = ""
                        creatingPage = false
                    },
                )
            }
        }
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

@Composable
private fun SmallTextButton(
    label: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
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
private fun StatusLine(state: CaptureState) {
    val message = when (state) {
        CaptureState.Capturing -> "Capturing..."
        CaptureState.Processing -> "Reading text..."
        is CaptureState.Error -> state.message
        else -> ""
    }
    if (message.isNotEmpty()) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = if (state is CaptureState.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotebookEntryView(
    entry: NotebookEntry,
    textSize: Int,
    onTextChange: (String, String) -> Unit,
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
                val bitmap = remember(entry.imagePath) {
                    entry.imagePath?.let { BitmapFactory.decodeFile(it)?.trimHorizontalBlackBars() }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp)),
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
