package com.wesbos.housecapture.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wesbos.housecapture.R
import com.wesbos.housecapture.capture.CoverageSnapshot
import com.wesbos.housecapture.capture.RoomLibrary
import com.wesbos.housecapture.capture.RoomSummary
import com.wesbos.housecapture.ui.theme.Fieldwork
import com.wesbos.housecapture.ui.theme.FieldworkButton
import com.wesbos.housecapture.ui.theme.FieldworkEyebrow
import com.wesbos.housecapture.ui.theme.FieldworkRule
import com.wesbos.housecapture.ui.theme.FieldworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed interface Loaded {
    data object Loading : Loaded
    data object Missing : Loaded
    data class Room(val summary: RoomSummary) : Loaded
}

/** A saved room: what was recorded, the plan of the scan when it was just captured, and sharing. */
@Composable
fun RoomScreen(library: RoomLibrary, sessionId: String, snapshot: CoverageSnapshot?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by produceState<Loaded>(Loaded.Loading, library, sessionId) {
        value = withContext(Dispatchers.IO) {
            runCatching { library.list().firstOrNull { it.sessionId == sessionId } }.getOrNull()
        }?.let { Loaded.Room(it) } ?: Loaded.Missing
    }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val chooserTitle = stringResource(R.string.share_room)

    when (val room = loaded) {
        Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Fieldwork.orange)
        }
        Loaded.Missing -> RoomMissing(onDone)
        is Loaded.Room -> RoomContent(
            room = room.summary,
            snapshot = snapshot,
            sharing = sharing,
            shareError = shareError,
            onShare = {
                sharing = true
                shareError = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { library.shareZip(sessionId) } }
                    sharing = false
                    result
                        .onSuccess { uri ->
                            val stream = Uri.parse(uri)
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("application/zip")
                                .putExtra(Intent.EXTRA_STREAM, stream)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            send.clipData = ClipData.newRawUri(room.summary.name, stream)
                            context.startActivity(Intent.createChooser(send, chooserTitle))
                        }
                        .onFailure { shareError = it.message ?: it.javaClass.simpleName }
                }
            },
            onDone = onDone,
        )
    }
}

@Composable
fun RoomContent(
    room: RoomSummary,
    snapshot: CoverageSnapshot?,
    sharing: Boolean,
    shareError: String?,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FieldworkEyebrow(sessionLabel(room.sessionId))
        Text(room.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Fieldwork.ink)
        FieldworkRule()

        if (snapshot != null) {
            Surface(
                color = Fieldwork.paper,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Fieldwork.rule),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            ) {
                PlanCanvas(snapshot, Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.plan_legend), style = MaterialTheme.typography.bodySmall, color = Fieldwork.secondary)
            FieldworkRule()
        }

        Fact(stringResource(R.string.fact_photos), room.photos.toString())
        Fact(stringResource(R.string.fact_depth), room.depthFrames.toString())
        Fact(stringResource(R.string.fact_walls), room.walls.toString())
        Fact(
            stringResource(R.string.fact_photo_detail),
            room.coverage?.let { stringResource(R.string.percent, (it * 100).roundToInt()) } ?: stringResource(R.string.not_measured),
        )
        Fact(stringResource(R.string.fact_size), Formatter.formatShortFileSize(context, room.bytes))
        FieldworkRule()
        FieldworkEyebrow(stringResource(R.string.fact_folder))
        Text(room.folderPath, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Fieldwork.secondary)
        Text(stringResource(R.string.room_next_steps), style = MaterialTheme.typography.bodySmall, color = Fieldwork.secondary)

        Spacer(Modifier.height(8.dp))
        FieldworkButton(
            text = stringResource(if (sharing) R.string.preparing_zip else R.string.share_room),
            onClick = onShare,
            enabled = !sharing,
        )
        if (shareError != null) {
            Text(stringResource(R.string.share_failed, shareError), style = MaterialTheme.typography.bodySmall, color = Fieldwork.orange)
        }
        FieldworkButton(text = stringResource(R.string.done), onClick = onDone, filled = false)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Fieldwork.secondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = Fieldwork.ink)
    }
}

@Composable
private fun RoomMissing(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.room_missing), style = MaterialTheme.typography.bodyMedium, color = Fieldwork.ink)
        FieldworkButton(text = stringResource(R.string.done), onClick = onDone, filled = false)
    }
}

@Preview(widthDp = 390, heightDp = 1000, showBackground = true, backgroundColor = 0xFFF6F2EB)
@Composable
private fun RoomPreview() {
    FieldworkTheme {
        RoomContent(PreviewData.rooms[0], PreviewData.snapshot, sharing = false, shareError = null, onShare = {}, onDone = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF6F2EB)
@Composable
private fun RoomNoPlanPreview() {
    FieldworkTheme {
        RoomContent(PreviewData.rooms[1], null, sharing = true, shareError = null, onShare = {}, onDone = {})
    }
}
