package com.wesbos.housecapture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wesbos.housecapture.R
import com.wesbos.housecapture.capture.CoverageSnapshot
import com.wesbos.housecapture.ui.theme.Fieldwork
import com.wesbos.housecapture.ui.theme.FieldworkTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Coverage cells are 10 cm voxels (CoverageGrid). */
private const val CELL_METERS = 0.1f

/** Smallest area shown, so the first few cells don't fill the card. */
private const val MIN_SPAN_METERS = 3f

/**
 * Top-down plan of a scan: world x to the right, world z down (looking at the floor from above).
 * Seen cells are grey, photographed cells green, ARCore planes blue, photos pink, the camera orange
 * with a tick along its heading. Scales to fit whatever has been seen so far.
 */
@Composable
fun PlanCanvas(snapshot: CoverageSnapshot?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (snapshot == null) {
            Text(
                stringResource(R.string.plan_empty),
                modifier = Modifier.padding(8.dp),
                color = Fieldwork.secondary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        } else {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) { drawPlan(snapshot) }
        }
    }
}

private class PlanBounds(snapshot: CoverageSnapshot) {
    var minX = snapshot.cameraX
    var maxX = snapshot.cameraX
    var minZ = snapshot.cameraZ
    var maxZ = snapshot.cameraZ

    init {
        include(snapshot.seen)
        include(snapshot.photographed)
        include(snapshot.photoPositions)
        snapshot.planes.forEach { include(it) }
        val padX = max(0f, MIN_SPAN_METERS - (maxX - minX)) / 2
        val padZ = max(0f, MIN_SPAN_METERS - (maxZ - minZ)) / 2
        minX -= padX; maxX += padX
        minZ -= padZ; maxZ += padZ
    }

    private fun include(xz: FloatArray) {
        var i = 0
        while (i + 1 < xz.size) {
            minX = min(minX, xz[i]); maxX = max(maxX, xz[i])
            minZ = min(minZ, xz[i + 1]); maxZ = max(maxZ, xz[i + 1])
            i += 2
        }
    }
}

private fun DrawScope.drawPlan(snapshot: CoverageSnapshot) {
    val bounds = PlanBounds(snapshot)
    val spanX = bounds.maxX - bounds.minX + CELL_METERS
    val spanZ = bounds.maxZ - bounds.minZ + CELL_METERS
    val scale = min(size.width / spanX, size.height / spanZ)
    val originX = (size.width - (bounds.maxX - bounds.minX) * scale) / 2 - bounds.minX * scale
    val originZ = (size.height - (bounds.maxZ - bounds.minZ) * scale) / 2 - bounds.minZ * scale
    fun at(x: Float, z: Float) = Offset(originX + x * scale, originZ + z * scale)
    fun points(xz: FloatArray) = List(xz.size / 2) { at(xz[2 * it], xz[2 * it + 1]) }

    val cell = max(1.5f, CELL_METERS * scale)
    drawPoints(points(snapshot.seen), PointMode.Points, Fieldwork.seen, strokeWidth = cell, cap = StrokeCap.Square)
    drawPoints(points(snapshot.photographed), PointMode.Points, Fieldwork.photographed, strokeWidth = cell, cap = StrokeCap.Square)

    val planeStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    for (polygon in snapshot.planes) {
        if (polygon.size < 4) continue
        val path = Path()
        val first = at(polygon[0], polygon[1])
        path.moveTo(first.x, first.y)
        for (i in 2 until polygon.size - 1 step 2) {
            val p = at(polygon[i], polygon[i + 1])
            path.lineTo(p.x, p.y)
        }
        if (polygon.size > 4) path.close()
        drawPath(path, Fieldwork.plane, style = planeStroke)
    }

    val photoRadius = 2.5.dp.toPx()
    for (p in points(snapshot.photoPositions)) drawCircle(Fieldwork.photo, photoRadius, p)

    val camera = at(snapshot.cameraX, snapshot.cameraZ)
    val tick = max(14.dp.toPx(), 0.5f * scale)
    val heading = snapshot.headingRadians
    drawLine(
        Fieldwork.orange,
        camera,
        Offset(camera.x + cos(heading) * tick, camera.y + sin(heading) * tick),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(Fieldwork.orange, 5.dp.toPx(), camera)
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFFF6F2EB)
@Composable
private fun PlanCanvasPreview() {
    FieldworkTheme { PlanCanvas(PreviewData.snapshot, Modifier.fillMaxSize()) }
}
