# 3D Model Anything for Android

Android app that scans a room into a `house-capture/5` bundle the three.js rebuild pipeline
(`scan-to-threejs` skill) can use. Per room it records, on one ARCore session:

- **Posed photos**: frames taken automatically where they add the most coverage and only while the
  phone is steady, plus a shutter for close-ups. Exact camera pose + intrinsics per photo.
- **Depth frames**: ARCore raw depth (Float16 meters) + confidence + a 640-wide color JPEG, up to 5/s.
  A frame is kept only when ARCore reports the depth image's timestamp as the camera frame's own, so
  pose, color and depth are meant to be from the same instant (not yet checked on a device).
- **Planes**: horizontal and vertical ARCore planes, turned into a minimal RoomPlan-shaped
  `rooms/room-N.json` so a fresh export already converts.

There is no LiDAR, scene mesh or RoomPlan on Android. The app's walls are plane-derived and
low/medium confidence; doors, windows, openings and furniture boxes are not detected on the phone.
`android_synthesize_room.py` in the skill builds them offline from the depth frames (see
[Android bundles](../../.agents/skills/scan-to-threejs/references/house-capture.md#android-bundles)).

Requires an Android 10+ (API 29) phone with ARCore and the
[Depth API](https://developers.google.com/ar/devices). Developed for the Pixel 10 Pro. Without depth
support the app still records photos and planes, but the offline synthesizer has nothing to work with.

## Build

The `Build Android app` workflow (`.github/workflows/android.yml`) runs the unit tests and
`assembleDebug` on every push that touches this directory. Download the
`house-capture-android-debug` artifact from the run and install `app-debug.apk`
(`adb install app-debug.apk`).

Locally, open `apps/house-capture-android` in Android Studio, or (Gradle 8.9+ and JDK 17 installed):

```sh
cd apps/house-capture-android
gradle wrapper --gradle-version 8.9      # once; the wrapper is not committed
./gradlew testDebugUnitTest assembleDebug
```

Toolchain: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, ARCore 1.57.0, compile/target SDK 35,
min SDK 29. JVM unit tests (`app/src/test`) cover the bundle JSON, matrices, depth encoding and the
fallback layout.

## Scanning

Tap **Scan a room**, name it (camera permission is asked here), then walk the room. The top chip
shows **photo detail** (the photographed fraction of depth-observed cells, not the share of the room
scanned) and counts; the plan card shows cells seen by depth (gray), well-photographed cells (green),
ARCore planes (blue), photos (pink) and the camera (orange). **Finish room** saves it; the room page
shares it as a zip. Delete a room from the library with its trash icon.

- Move slowly and sideways so ARCore gets parallax; depth needs motion, not just rotation.
- Keep surfaces 0.5–5 m away. Blank walls, glass and mirrors return little or no depth.
- Light the room and point at textured areas when tracking warns about missing detail.
- Walk back to where you started (close the loop) to reduce drift.

Photos, depth and coverage input stop while tracking is not normal. One scan is one session folder
with one room, always room index 0. Pausing the app (a call, switching apps) and coming back starts a
new recording segment in the same room; ARCore may relocalize after a resume, so the segments are not
guaranteed to share one world frame. Time in the background is not counted.

Leaving a scan without **Finish room** keeps it in the library as an "Unfinished scan". The app
checkpoints photo and depth records and a plane layout every five seconds, so a scan that was left,
crashed or killed should lose only its last few seconds; sharing it adds the room entry first. A scan
that recorded nothing is deleted. These recovery paths are tested only off-device.

## On disk

```
files/Houses/android/sessions/<yyyy-MM-dd HH.mm.ss>/   one session = one scan bundle, house-capture/5:
  manifest.json            rooms, files, device, alignment; platform "android", capture (ARCore details)
  rooms/room-N.json        RoomPlan-shaped layout from ARCore planes (rewritten by the synthesizer)
  rooms/room-N.arcore.json the app's original plane layout, never overwritten
  photos/photos.json       [{ file, room, timestamp, width, height, transform, intrinsics, tracking,
                              manual, exposure, blurPixels, pass, highRes, camera, segment }]
  photos/NNNN.jpg          largest ARCore CPU image (expected 1920×1080), sensor orientation (EXIF 6)
  depth/frames.json        [{ file, confidence, rgb, rgbIntrinsics, camera, room, timestamp, width,
                              height, transform, intrinsics, arcore, segment }]
  depth/NNNNN.f16z         raw depth, Float16 meters, raw DEFLATE: zlib.decompress(data, -15)
  depth/NNNNN.conf         UInt8 confidence (0–2, quantized from ARCore's 0–255)
  depth/NNNNN.jpg          640-wide color of the same frame (no EXIF)
```

Compared with an iPhone bundle there is no `structure.json|usdz`, `worldmap.arworldmap`,
`rooms/room-N-data.json`, `.usdz` or `mesh/` (`rooms[].usdz` is `""`; `manifest.mesh` is absent until
`android_synthesize_room.py --write-bundle` adds `mesh/room-N.ply`). Optional keys are omitted rather
than written as null; on a phone without depth there is no `depth/` data, `frames.json` or
`depthFrames`. New optional keys: `manifest.platform` (`"android"`), `manifest.capture` (engine,
ARCore SDK, chosen camera config, depth mode and size, `layoutSource`, `synthesized`, worst sampled
`thermal` status, `segments` count, and `events: [{t, what}]` with `pause`, `resume` and
`tracking:<state>` at recorded seconds `t`), per-record `segment` (0-based, on photos and depth
frames) and per-depth-frame `arcore` (texture size, texture intrinsics, depth timestamp). The iOS
app and the Python scripts ignore unknown keys; no script reads `segment` yet. During capture the
manifest (with `rooms: []`), `photos.json`, `frames.json` and the plane layout are checkpointed every
five seconds; Finish room rewrites them and adds the room entry.

**Coordinates**: ARCore world space is meters, +Y up (gravity), right-handed — the same as an
ARKit session. Photo and depth `transform` is `Frame.getCamera().getPose()` (the sensor camera, not
the display-oriented pose) as a column-major 4×4, camera-to-world, looking down −Z. JPEG pixels are
the raw landscape sensor image and `intrinsics` (fx, fy, cx, cy) match them — ignore EXIF when
projecting. Depth intrinsics are ARCore's texture intrinsics scaled to the depth image size; the raw
values are kept in `arcore` so they can be corrected offline.

## Sharing and importing

Open a saved room → **Share room (.zip)**. The zip has `manifest.json` at its root, like the iOS
export. Extract it into an ignored `data/captures-in/<room>/` directory and follow
[Android bundles](../../.agents/skills/scan-to-threejs/references/house-capture.md#android-bundles).
The app makes no network requests. Exports keep the keys the iOS importer requires, so a
non-LiDAR iPhone may be able to import and review them (boxes only, no mesh) — not verified.

## Not verified

The app is checked only by the CI build and JVM unit tests. The following need a real Pixel 10 Pro:

1. Pose and image orientation: `prepare_photos.py` output is upright and `photos_for.py` points land
   where expected.
2. Depth intrinsics between the CPU image and the depth map: synthesized walls line up with photos
   in `bundle_plan.py --room N`, and a known 3 m wall measures 3 ± 0.1 m.
3. The largest CPU camera config is at least 1920×1080 (the chosen sizes are in `manifest.capture`).
4. Synthesized walls, openings and objects on a real room against a tape measure.
5. Drift on long scans (doubled walls).
6. Segments after a pause may not line up: whether ARCore keeps one world frame across
   pause/resume. Check `manifest.capture.segments`/`events` and compare walls per `segment`.
7. Depth/pose pairing: every frame in `depth/frames.json` has `arcore.depthTimestamp == timestamp`,
   and enough frames pass that test to fuse a room.
8. Unfinished-scan recovery after the app is killed in the background.
9. Thermal behavior on long scans.
10. Exposure metadata for the blur gate (falls back to 1/60 s).
11. ARCore update prompt on devices with an older Google Play Services for AR.
12. Importing an Android zip on an iPhone.

No depth accuracy figure, reliable door/window/object detection, or equivalence to a LiDAR scan is
claimed.
