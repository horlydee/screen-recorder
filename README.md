# Screen Recorder (personal use)

A minimal Android app that records your screen + mic using the official
MediaProjection API — the same mechanism every screen recorder app uses.
No third-party SDKs, no ads, no lag from a bloated app.

## What it does
- One button: Start / Stop Recording, plus a quality picker
- Runs as a foreground service so it keeps recording while you play
- Saves MP4s to **Movies/ScreenRecordings** on your phone
- Two quality profiles, tuned for low-end hardware like the Galaxy A03s:
  - **Low**: scales capture down to a 480px short edge, 15fps, 1.8 Mbps — lightest
    load on the CPU/GPU, use this while actually gaming
  - **Balanced**: 720px short edge, 24fps, 4 Mbps — better clarity, use only if
    Low still looks fine performance-wise and you want sharper footage

### Why this matters on the A03s specifically
The A03s runs a MediaTek Helio P35 — a budget chipset with a weak GPU and only
3-4GB RAM. Recording at native resolution + 60fps (a common default) forces the
GPU to composite a second full-res video feed on top of rendering your actual
game, which is exactly what causes recording to make your gameplay lag *worse*.
Scaling the capture resolution down (not just the bitrate) is what actually
relieves that load — that's what the quality picker does under the hood.

**Start with "Low."** If it looks acceptable and your game still runs smoothly,
try "Balanced" for sharper clips. If Balanced causes noticeable lag, drop back
to Low — this is the single most impactful setting on your device.

## How to build it

You have two options — pick whichever fits your situation.

### Option A: Android Studio (on your laptop)
1. Install **Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio → "Open" → select this `ScreenRecorder` folder
3. Let it sync Gradle (first time takes a few minutes, downloads dependencies —
   needs internet)
4. Plug your Android phone into the computer via USB
5. On your phone: enable **Developer Options** (Settings → About Phone → tap
   "Build Number" 7 times), then enable **USB Debugging** inside Developer Options
6. In Android Studio, select your phone from the device dropdown (top toolbar)
   and hit the green ▶ Run button
7. The app installs and launches on your phone directly — no Play Store needed

### Option B: Cloud build via GitHub Actions (no big download needed)
Best if your connection makes a 1.4GB+ Android Studio download painful. Instead,
GitHub's own servers compile the app for you — you only ever download the
finished APK (a few MB) at the end.

1. Create a free account at https://github.com if you don't have one
2. Create a new repository (top right → "+" → "New repository"), any name,
   keep it "Public" or "Private," doesn't matter
3. On the new repo's page, click "uploading an existing file" and drag in
   this entire `ScreenRecorder` folder (or use `git push` from a terminal if
   you're comfortable with that)
4. Go to the **Actions** tab on your repo — it should automatically start
   building (the workflow file is already included in this project). Takes
   about 2-4 minutes.
5. Once it finishes (green checkmark), click into that run, scroll to
   **Artifacts**, and download `ScreenRecorder-debug-apk` — this is a zip
   containing the APK
6. Transfer that APK to your phone (email it to yourself, Google Drive, USB
   cable — whatever's easiest) and tap it to install
7. Your phone will ask to allow "install from unknown sources" for whichever
   app you used to open it — allow it, then install

**Trade-off**: every time you want to change something in the code (like
tweaking bitrate), you'd re-upload the changed file and wait for Actions to
rebuild — there's no instant "run and test" like Android Studio gives you.
For a finished app you're not constantly editing, that's a fine trade for
skipping the big download entirely.

## Using it
1. Open the app, tap "Start Recording"
2. Android will show a system dialog asking permission to record the screen —
   tap "Start now"
3. Minimize the app (don't force-close it) and go play — a persistent
   notification shows recording is active
4. Pull down notifications or reopen the app and tap "Stop Recording"
5. Find your clip in your phone's Gallery/Files app under Movies → ScreenRecordings

## If you still hit lag on "Low"
- Open `ScreenRecordService.kt`, find the `QUALITY_LOW` line, and try dropping
  the bitrate further (e.g. `1_800_000` → `1_200_000`) or the frame rate
  (`15` → `10`). Re-run in Android Studio to rebuild after any change.
- Close other background apps before recording — on 3GB RAM especially, every
  bit of free memory matters when the encoder is running.
- Make sure nothing else (Discord, Facebook, etc.) is capturing screen/overlay
  at the same time — overlays compound the GPU load.

## Notes
- `minSdk = 26` (Android 8.0+) — should cover basically any phone from the
  last several years
- The app records **internal mic audio**, not game/system audio directly —
  full system-audio capture requires Android 10+ `AudioPlaybackCaptureConfiguration`,
  which I can add next if you want in-game sound baked into the recording
  instead of just your mic/voice
