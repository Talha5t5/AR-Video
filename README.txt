AR Video App — Augmented Image → Video Overlay
==============================================

WHAT THIS APP DOES
──────────────────
When the Android camera detects a specific image (poster, flyer, business card, etc.),
a video immediately plays overlaid on top of that image in AR, perfectly aligned
and scaled to the physical image dimensions. Speed-optimised to match Stories AR app behaviour.


QUICK START
───────────

STEP 1 — Add your images & videos
   • Place your target images in a folder, e.g.  tools/images/
     (Supported formats: JPG, PNG)
   • Name them clearly, e.g.  poster.jpg, flyer.jpg, business_card.jpg
   • Place your corresponding videos in:
       app/src/main/assets/videos/
     with matching names, e.g.  poster.mp4, flyer.mp4, business_card.mp4

STEP 2 — Check image quality score (CRITICAL for detection speed)
   Download arcoreimg CLI from:
   https://github.com/google-ar/arcore-android-sdk/tree/master/tools/arcoreimg

   Check each image:
     ./arcoreimg eval-img --input_image_path=poster.jpg
     # Score must be >= 75 for fast reliable detection

STEP 3 — Build the image database
   ./arcoreimg build-db \
     --input_images_directory=./tools/images/ \
     --output_db_path=./app/src/main/assets/ar_images.imgdb

   ⚠️  The file MUST be named:  ar_images.imgdb
   ⚠️  Place it in:  app/src/main/assets/

STEP 4 — Update the image→video mapping in code
   Open:  app/src/main/kotlin/com/example/arvideo/ArVideoActivity.kt
   Find the `videoConfig` variable and update it:

     private val videoConfig = mapOf(
         "poster" to VideoData(
             displayName = "My Poster",
             imageName   = "poster.jpg",
             videoPath   = "videos/poster.mp4",
             width       = 0.20f       // physical width in meters used by ARCore
         ),
         "flyer" to VideoData(
             displayName = "My Flyer",
             imageName   = "flyer.jpg",
             videoPath   = "videos/flyer.mp4",
             width       = 0.18f
         )
     )

   • The MAP KEY (e.g. "poster") must match the image name stored in `ar_images.imgdb`.
   • `imageName` is the filename in `assets/images/`.
   • `videoPath` is the relative path inside the app assets videos folder, e.g. `videos/poster.mp4`.
   • `width` should match the real‑world physical width you used when building the imgdb
     (or set it here and regenerate the database with the same values).

STEP 5 — Build & run
   Open the project in Android Studio (Hedgehog 2023.1 or later).
   Connect an ARCore-compatible Android device (API 24+).
   Click Run ▶


PROJECT STRUCTURE
─────────────────
ARVideoApp/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── ar_images.imgdb          ← PUT YOUR COMPILED DB HERE
│   │   │   └── videos/
│   │   │       ├── poster.mp4           ← PUT YOUR VIDEOS HERE
│   │   │       └── ...
│   │   ├── kotlin/com/example/arvideo/
│   │   │   ├── ArVideoActivity.kt       ← Main AR logic
│   │   │   └── ExoPlayerPool.kt         ← Multi-player pool
│   │   ├── res/
│   │   │   ├── layout/activity_ar_video.xml
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/gradle-wrapper.properties


PERFORMANCE TIPS
────────────────
✅ Always use pre-compiled .imgdb (never add images at runtime)
✅ Image quality score must be >= 75
✅ Use H.264/AVC video codec for fastest hardware decode
✅ Keep videos at 720p–1080p (4K slows rendering)
✅ Use MP4 container (fastest seek/start)
✅ ExoPlayer pool pre-warms players before detection
✅ Config.UpdateMode.LATEST_CAMERA_IMAGE for lowest latency
✅ Config.FocusMode.AUTO for continuous fast focus
✅ Plane finding & light estimation disabled (saves CPU)
✅ Shadow casting disabled on video nodes


REQUIREMENTS
────────────
• Android Studio Hedgehog (2023.1.1) or later
• Android device with ARCore support
• Android API 24 (Android 7.0) minimum
• ARCore 1.44.0
• SceneView 2.2.1
• Media3 ExoPlayer 1.3.1
• Kotlin 1.9.23
• Gradle 8.6


HOW TO ADD MORE IMAGE/VIDEO PAIRS
──────────────────────────────────
1. Add your new image to  tools/images/new_image.jpg   (or reuse the existing tools folder you prefer)
2. Add your new video to   app/src/main/assets/videos/new_video.mp4
3. Re-run `arcoreimg build-db` to regenerate `ar_images.imgdb` with the new image:
     ./arcoreimg build-db \
       --input_images_directory=./tools/images/ \
       --output_db_path=./app/src/main/assets/ar_images.imgdb
4. Add an entry to `videoConfig` in `ArVideoActivity.kt`, for example:

     "new_image" to VideoData(
         displayName = "My New Experience",
         imageName   = "new_image.jpg",
         videoPath   = "videos/new_video.mp4",
         width       = 0.20f
     )

5. Rebuild and run the app on an ARCore device.


RUNTIME BEHAVIOUR
─────────────────
• When the camera clearly sees a registered target image, the app:
  – Creates an AR plane exactly on top of the image
  – Starts the corresponding video from the local assets using ExoPlayer
• If the camera loses tracking of that image:
  – The video is paused
  – The AR plane is hidden
• When the same image is seen again, the video resumes from where it stopped.
• If multiple known images are visible, each has its own video/plane, but playback
  may be limited by the ExoPlayer pool size (`poolSize` in `ExoPlayerPool`).


TROUBLESHOOTING
───────────────
• "Failed to load image database" → Make sure ar_images.imgdb is in assets/
• Video not detected              → Check image quality score (must be >= 75)
• Video floats off image          → Image physical size not set in imgdb; use
                                    --physical_size flag in arcoreimg build-db
• Black screen on start           → ExoPlayer buffering; use local assets (not URL)
• Multiple images crash           → Pool size too small; increase poolSize in ExoPlayerPool
• App crashes on launch           → Device doesn't support ARCore


ARCOREIMG DOWNLOAD
──────────────────
https://github.com/google-ar/arcore-android-sdk/releases
→ Download the SDK zip
→ Extract tools/arcoreimg/

ARCORE COMPATIBLE DEVICES
──────────────────────────
https://developers.google.com/ar/devices
