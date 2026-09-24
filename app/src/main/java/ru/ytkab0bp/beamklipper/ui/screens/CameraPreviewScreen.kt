package ru.ytkab0bp.beamklipper.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.BrutalButton
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

// Local MJPEG endpoint served by CameraService (same stream Fluidd/Mainsail use).
private const val STREAM_URL = "http://127.0.0.1:8889/"

@Composable
fun CameraPreviewScreen() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var frame by remember { mutableStateOf<Bitmap?>(null) }

    // Only connected while this tab is on screen — CameraService skips JPEG
    // encoding entirely when nobody is watching, so leaving the tab is free.
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        while (true) {
            try {
                streamFrames { frame = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                frame = null
            }
            delay(1000)
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.CameraPreview), style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(12.dp))
        if (!granted) {
            Text(stringResource(R.string.CameraPreviewPermission), color = Ink)
            Spacer(Modifier.height(12.dp))
            BrutalButton(
                text = stringResource(R.string.CameraPreviewGrant),
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            return@Column
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(Color.Black, RectangleShape)
                .border(2.dp, Ink, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            val bmp = frame
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    stringResource(R.string.CameraPreviewConnecting),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.CameraPreviewHint), style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

// Parses CameraService's multipart/x-mixed-replace stream: each part is
// "--camera-frame", headers incl. Content-Length, blank line, JPEG bytes.
private suspend fun streamFrames(onFrame: (Bitmap) -> Unit) = withContext(Dispatchers.IO) {
    val conn = (URL(STREAM_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 3000
        readTimeout = 5000
    }
    try {
        val input = DataInputStream(BufferedInputStream(conn.inputStream, 64 * 1024))
        while (true) {
            coroutineContext.ensureActive()
            var length = -1
            while (true) {
                val line = input.readHeaderLine() ?: return@withContext
                if (line.isEmpty()) {
                    if (length >= 0) break else continue
                }
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: -1
                }
            }
            val data = ByteArray(length)
            input.readFully(data)
            BitmapFactory.decodeByteArray(data, 0, data.size)?.let { bmp ->
                withContext(Dispatchers.Main) { onFrame(bmp) }
            }
        }
    } finally {
        conn.disconnect()
    }
}

private fun InputStream.readHeaderLine(): String? {
    val sb = StringBuilder()
    while (true) {
        val c = read()
        if (c == -1) return if (sb.isEmpty()) null else sb.toString()
        if (c == '\n'.code) return sb.toString().trimEnd('\r')
        sb.append(c.toChar())
    }
}
