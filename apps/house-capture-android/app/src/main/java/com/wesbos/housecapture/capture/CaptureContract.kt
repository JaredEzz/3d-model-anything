package com.wesbos.housecapture.capture

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

/** What the scanning UI can observe and ask for. Implemented by CaptureController; UI never sees ARCore types. */
interface CaptureContract {
    val state: StateFlow<CaptureState>
    /** The camera preview surface (a GLSurfaceView). Create once per Scan screen; attach with AndroidView. */
    fun createPreviewView(context: Context): View
    fun onResume()
    fun onPause()
    /** Begin recording a room into a new session folder. */
    fun startRoom(name: String)
    fun takePhoto()
    /** Stop recording, flush writers, write room JSON + manifest. Result arrives in state.lastSaved. */
    fun finishRoom()
    fun release()
}

enum class Phase { IDLE, STARTING, SCANNING, FINISHING, SAVED, FAILED }

data class CaptureState(
    val phase: Phase = Phase.IDLE,
    /** "normal", "limited:<reason>", "notAvailable" */
    val tracking: String = "notAvailable",
    /** human-readable hint while tracking is not normal, else null */
    val trackingWarning: String? = null,
    val depthAvailable: Boolean = false,
    val photos: Int = 0,
    val manualPhotos: Int = 0,
    val depthFrames: Int = 0,
    val planes: Int = 0,
    /** 0..1 photographed fraction of seen cells; null until something was seen */
    val coverage: Float? = null,
    val elapsedSeconds: Int = 0,
    val snapshot: CoverageSnapshot? = null,
    val lastSaved: RoomSummary? = null,
    val error: String? = null,
)

/** Top-down (x, z) picture of the scan for the plan canvas. World meters. */
data class CoverageSnapshot(
    val seen: FloatArray,          // x0,z0,x1,z1,... cells with depth
    val photographed: FloatArray,  // subset with good photo coverage
    val planes: List<FloatArray>,  // each: x0,z0,x1,z1,... polygon (vertical planes as 2-point segments)
    val cameraX: Float, val cameraZ: Float, val headingRadians: Float,
    val photoPositions: FloatArray, // x,z per photo
)

data class RoomSummary(
    val sessionId: String,
    val roomIndex: Int,
    val name: String,
    val folderPath: String,
    val photos: Int, val depthFrames: Int, val walls: Int,
    val coverage: Float?,
    val bytes: Long,
)

/** Saved rooms on disk, for the library and sharing. Implemented by store/HouseStore. */
interface RoomLibrary {
    fun list(): List<RoomSummary>
    fun delete(sessionId: String)
    /** Zips the session and returns a content:// Uri string ready for ACTION_SEND. */
    fun shareZip(sessionId: String): String
}

/** Entry points WP2 uses to obtain implementations without a DI framework. */
object CaptureFactory {
    lateinit var newController: (Context) -> CaptureContract
    lateinit var library: (Context) -> RoomLibrary
}
