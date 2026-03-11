package com.example.arvideo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.arvideo.databinding.ActivityArVideoBinding
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.PlaneNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.widget.Button

class ArVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArVideoBinding
    private lateinit var playerPool: ExoPlayerPool
    private val activeNodes        = mutableMapOf<String, AnchorNode>()
    private val activePoolItems    = mutableMapOf<String, ExoPlayerPool.PlayerInstance>()
    private val lastTrackingState  = mutableMapOf<String, TrackingState>()
    private val videoPlaneNodes   = mutableMapOf<String, PlaneNode>()
    private val preloadedPlayers   = mutableMapOf<String, ExoPlayerPool.PlayerInstance>()
    private var firstDetected = false

    // Configuration
    private val videoConfig = mapOf(
        "test"           to VideoData("test", "test.png", "videos/test.mp4", 0.1f),
        "book"           to VideoData("BRS Physiology", "book.png", "videos/book.mp4", 0.22f)
    )

    data class VideoData(
        val displayName: String,
        val imageName: String,
        val videoPath: String,
        val width: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTargetList()
        setupPermissionUI()
        setupShowListButton()

        if (!hasCameraPermission()) {
            showPermissionScreen(true)
        } else {
            showPermissionScreen(false)
            showTargetList(true)
            initAR()
        }
    }

    private fun setupShowListButton() {
        binding.btnShowList.setOnClickListener {
            showTargetList(true)
        }
    }

    private fun showTargetList(show: Boolean) {
        val layout = findViewById<LinearLayout>(R.id.layoutTargetList)
        layout?.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnShowList.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showPermissionScreen(show: Boolean) {
        val layout = findViewById<LinearLayout>(R.id.layoutPermission)
        layout?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupPermissionUI() {
        val btnGrant = findViewById<Button>(R.id.btnGrantPermission)
        btnGrant?.setOnClickListener {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    private fun setupTargetList() {
        val container = findViewById<LinearLayout>(R.id.targetItemsContainer) ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        videoConfig.forEach { (id, data) ->
            val itemView = inflater.inflate(R.layout.item_target_card, container, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvItemName)
            val ivThumb = itemView.findViewById<ImageView>(R.id.ivItemThumbnail)

            tvName.text = data.displayName
            
            try {
                assets.open("images/${data.imageName}").use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    ivThumb.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed thumb: ${data.imageName}", e)
            }

            itemView.setOnClickListener {
                showTargetList(false)
                Toast.makeText(this, "Scan the ${data.displayName}", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = "Searching: ${data.displayName}"
            }

            container.addView(itemView)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            showPermissionScreen(false)
            showTargetList(true)
            initAR()
        } else {
            Toast.makeText(this, "Camera permission is required for AR", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initAR() {
        playerPool = ExoPlayerPool(
            context = this,
            engine = binding.arSceneView.engine,
            materialLoader = binding.arSceneView.materialLoader,
            poolSize = 4
        )
        preLoadVideos()
        setupARSession()
        observeARFrames()
        Log.d(TAG, "AR initialized with Pre-warming ✅")
    }

    private fun preLoadVideos() {
        // Preload all videos and keep them ready for instant playback
        videoConfig.forEach { (name, data) ->
            val poolItem = playerPool.acquire() ?: return@forEach
            val player = poolItem.player
            val targetMedia = "asset:///${data.videoPath}"
            
            player.setMediaItem(MediaItem.fromUri(targetMedia))
            player.playWhenReady = true // Ready to play immediately
            player.prepare() // Prepare in background
            
            // Keep player ready but paused until image is detected
            player.pause()
            
            preloadedPlayers[name] = poolItem
            Log.d(TAG, "Preloaded video: $name")
        }
    }

    private fun setupARSession() {
        binding.arSceneView.configureSession { session, config ->
            try {
                val db = assets.open(IMAGE_DB_ASSET).use { stream ->
                    AugmentedImageDatabase.deserialize(session, stream)
                }
                config.augmentedImageDatabase = db
            } catch (e: Exception) {
                val db = AugmentedImageDatabase(session)
                videoConfig.forEach { (name, data) ->
                    try {
                        assets.open("images/${data.imageName}").use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            db.addImage(name, bitmap, data.width)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed image: $name", e)
                    }
                }
                config.augmentedImageDatabase = db
            }
            
            config.focusMode = Config.FocusMode.AUTO
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.depthMode = Config.DepthMode.DISABLED
        }
    }

    private fun observeARFrames() {
        binding.arSceneView.onSessionUpdated = { session, frame ->
            // Get updated trackables for new detections
            val updated = frame.getUpdatedTrackables(AugmentedImage::class.java)
            // Get all trackables to check active images
            val allTrackables = session.getAllTrackables(AugmentedImage::class.java)
            val trackedImageMap = allTrackables.associateBy { it.name }
            
            // First, check all active videos and pause if not TRACKING
            for (activeName in activeNodes.keys.toList()) {
                val image = trackedImageMap[activeName]
                val player = activePoolItems[activeName]?.player
                val planeNode = videoPlaneNodes[activeName]
                
                if (image == null) {
                    // Image completely disappeared
                    player?.pause()
                    planeNode?.isVisible = false
                    cleanupImage(activeName)
                    continue
                }
                
                when (image.trackingState) {
                    TrackingState.TRACKING -> {
                        // Image is tracking - ensure video plays and plane is visible
                        if (player != null && !player.isPlaying) {
                            player.play()
                        }
                        // Show plane immediately - video is preloaded
                        if (planeNode != null && !planeNode.isVisible) {
                            planeNode.isVisible = true
                        }
                    }
                    TrackingState.PAUSED, TrackingState.STOPPED -> {
                        // Image not visible - PAUSE immediately and hide plane
                        if (player != null && player.isPlaying) {
                            player.pause()
                        }
                        planeNode?.isVisible = false
                        if (image.trackingState == TrackingState.STOPPED) {
                            cleanupImage(activeName)
                        }
                    }
                }
            }
            
            // Handle new detections from updated trackables
            for (image in updated) {
                val name = image.name
                if (name !in videoConfig) continue
                
                val prevState = lastTrackingState[name]
                val newState = image.trackingState
                
                when {
                    newState == TrackingState.TRACKING && prevState != TrackingState.TRACKING -> {
                        // New detection or resuming from paused
                        if (!activeNodes.containsKey(name)) {
                            attachVideoNode(image)
                        }
                    }
                }
                lastTrackingState[name] = newState
            }
        }
    }

    private fun attachVideoNode(image: AugmentedImage) {
        val name = image.name
        val config = videoConfig[name] ?: return
        
        // Use preloaded player if available, otherwise acquire from pool
        val poolItem = preloadedPlayers.remove(name) ?: playerPool.acquire() ?: return
        val player = poolItem.player

        // If not preloaded, set up the media
        val targetMedia = "asset:///${config.videoPath}"
        if (player.currentMediaItem?.localConfiguration?.uri?.toString() != targetMedia) {
            player.setMediaItem(MediaItem.fromUri(targetMedia))
            player.playWhenReady = true
            player.prepare()
        } else {
            // Preloaded player - start immediately!
            player.playWhenReady = true
            player.play()
        }
        
        activePoolItems[name] = poolItem
        binding.tvStatus.text = "Playing: ${config.displayName}"

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val engine = binding.arSceneView.engine
                val anchorNode = AnchorNode(engine = engine, anchor = image.createAnchor(image.centerPose))
                anchorNode.isEditable = false

                val planeNode = PlaneNode(
                    engine = engine,
                    size = io.github.sceneview.math.Size(image.extentX, image.extentZ),
                    center = Position(0f, 0f, 0f),
                    normal = Direction(0f, 1f, 0f),
                    materialInstance = poolItem.material.instance
                ).apply {
                    rotation = Rotation(-90.0f, 0.0f, 0.0f)
                    isVisible = true // Show immediately - video is preloaded
                }

                anchorNode.addChildNode(planeNode)
                binding.arSceneView.addChildNode(anchorNode)
                activeNodes[name] = anchorNode
                videoPlaneNodes[name] = planeNode

                if (!firstDetected) {
                    firstDetected = true
                    binding.layoutHint.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Attach fail: $name", e)
                playerPool.release(poolItem)
                activePoolItems.remove(name)
            }
        }
    }

    private fun cleanupImage(name: String) {
        activeNodes.remove(name)?.let {
            binding.arSceneView.removeChildNode(it)
            it.destroy()
        }
        videoPlaneNodes.remove(name)
        activePoolItems.remove(name)?.let { playerPool.release(it) }
        lastTrackingState.remove(name)
        binding.tvStatus.text = "Ready"
    }

    override fun onResume() {
        super.onResume()
        activePoolItems.values.forEach { it.player.play() }
    }

    override fun onPause() {
        activePoolItems.values.forEach { it.player.pause() }
        super.onPause()
    }

    override fun onDestroy() {
        activeNodes.values.forEach { it.destroy() }
        preloadedPlayers.values.forEach { playerPool.release(it) }
        preloadedPlayers.clear()
        if (::playerPool.isInitialized) playerPool.releaseAll()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ARVideo"
        private const val IMAGE_DB_ASSET = "ar_images.imgdb"
        private const val CAMERA_PERMISSION_CODE = 1001
    }
}
