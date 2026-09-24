package ru.ytkab0bp.beamklipper.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.components.brutalScrollbarHorizontal
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.InkOnAccent
import ru.ytkab0bp.beamklipper.ui.theme.Paper
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt
import ru.ytkab0bp.beamklipper.utils.BeamLogs

@Composable
fun LogsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sources = remember { BeamLogs.sources(context) }
    var selectedId by remember { mutableStateOf(sources.firstOrNull()?.id ?: "app") }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var reloadTick by remember { mutableStateOf(0) }
    // Resolved here (Activity-scoped, so it follows the in-app language)
    // because the click handlers and the loader below can't call stringResource().
    val sourceNotFoundText = stringResource(R.string.LogsSourceNotFound)
    val loadFailedFmt = stringResource(R.string.LogsLoadFailed, "%s")
    val loadingText = stringResource(R.string.LogsLoading)
    val refreshText = stringResource(R.string.LogsRefresh)
    val copyText = stringResource(R.string.LogsCopy)
    val copiedText = stringResource(R.string.LogsCopied)
    val downloadText = stringResource(R.string.LogsDownload)
    val savedFmt = stringResource(R.string.LogsSavedTo, "%s")
    val saveFailedText = stringResource(R.string.LogsSaveFailed)
    val shareText = stringResource(R.string.LogsShare)
    val shareTitleText = stringResource(R.string.LogsShareTitle)

    LaunchedEffect(selectedId, reloadTick) {
        loading = true
        val src = sources.firstOrNull { it.id == selectedId }
        content = if (src == null) sourceNotFoundText else withContext(Dispatchers.IO) {
            runCatching { src.load() }.getOrElse { loadFailedFmt.format(it.message) }
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Text(
            text = "LOGS",
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        val tabsScrollState = rememberScrollState()
        val tabsScope = rememberCoroutineScope()
        // Drag-to-scroll alone turned out not to be reliably reachable on
        // every input method (reports of it not responding to touch or to a
        // mouse) — these buttons scroll by a fixed step and work
        // unconditionally regardless of what's intercepting the drag
        // gesture itself, and double as a first-class affordance for a
        // mouse pointer (a click, not a click-and-drag).
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogsTabScrollButton(
                enabled = tabsScrollState.value > 0,
                rotated = true,
                onClick = {
                    tabsScope.launch {
                        tabsScrollState.animateScrollBy(-320f, tween(200))
                    }
                }
            )
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .brutalScrollbarHorizontal(tabsScrollState)
                    .horizontalScroll(tabsScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEach { src ->
                    val active = src.id == selectedId
                    Box(
                        modifier = Modifier
                            .clip(RectangleShape)
                            .background(if (active) Accent else Paper, RectangleShape)
                            .border(2.dp, Ink, RectangleShape)
                            .clickable { selectedId = src.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = src.label,
                            color = if (active) InkOnAccent else Ink,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
                // Without this, the last tab sits flush against the
                // scrollable viewport's edge — cut off mid-label before you
                // scroll reads as clipped/broken rather than "there's more".
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(6.dp))
            LogsTabScrollButton(
                enabled = tabsScrollState.value < tabsScrollState.maxValue,
                rotated = false,
                onClick = {
                    tabsScope.launch {
                        tabsScrollState.animateScrollBy(320f, tween(200))
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(PaperAlt, RectangleShape)
                .border(2.dp, Ink, RectangleShape)
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = if (loading) loadingText else content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Ink,
                    softWrap = false
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalButton(
                text = refreshText,
                onClick = { reloadTick++ },
                modifier = Modifier.weight(1f),
                background = Paper,
                contentColor = Ink
            )
            BrutalButton(
                text = copyText,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Kocoa Beam logs", content))
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                background = Paper,
                contentColor = Ink
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalButton(
                text = downloadText,
                onClick = {
                    scope.launch {
                        val where = withContext(Dispatchers.IO) { BeamLogs.saveForSharing(context) }
                        Toast.makeText(
                            context,
                            if (where != null) savedFmt.format(where) else saveFailedText,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f),
                background = Paper,
                contentColor = Ink
            )
            BrutalButton(
                text = shareText,
                onClick = {
                    scope.launch {
                        val blob = withContext(Dispatchers.IO) { BeamLogs.combined(context) }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Kocoa Beam logs")
                            putExtra(Intent.EXTRA_TEXT, blob)
                        }
                        context.startActivity(Intent.createChooser(send, shareTitleText))
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LogsTabScrollButton(
    enabled: Boolean,
    rotated: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RectangleShape)
            .background(if (enabled) Paper else PaperAlt, RectangleShape)
            .border(2.dp, Ink, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_28),
            contentDescription = null,
            tint = if (enabled) Ink else InkMuted,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (rotated) 180f else 0f)
        )
    }
}
