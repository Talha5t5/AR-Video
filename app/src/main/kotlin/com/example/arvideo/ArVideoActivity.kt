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
    private var firstDetected = false

    // Configuration
    private val videoConfig = mapOf(
        "test"           to VideoData("test", "test.png", "videos/test.mp4", 0.1f),
        "bunny_poster"   to VideoData("Big Buck Bunny", "bunny_poster.png", "videos/bunny_poster.mp4", 0.5f),
        "elephant_flyer" to VideoData("Elephant Dream", "elephant_flyer.png", "videos/elephant_flyer.mp4", 0.21f),
        "spaceship_card" to VideoData("Cosmic Voyager", "spaceship_card.png", "videos/test.mp4", 0.085f)
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
        val assetsToPreload = videoConfig.values.take(4) 
        assetsToPreload.forEach { data ->
            val poolItem = playerPool.acquire() ?: return@forEach
            poolItem.player.setMediaItem(MediaItem.fromUri("asset:///${data.videoPath}"))
            poolItem.player.prepare()
            poolItem.player.playWhenReady = false
            playerPool.release(poolItem)
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
        binding.arSceneView.onSessionUpdated = { _, frame ->
            val updated = frame.getUpdatedTrackables(AugmentedImage::class.java)
            for (image in updated) {
                val name = image.name
                val prevState = lastTrackingState[name]
                val newState = image.trackingState

                when {
                    newState == TrackingState.TRACKING -> {
                        if (prevState != TrackingState.TRACKING) {
                            if (activeNodes.containsKey(name)) {
                                activePoolItems[name]?.player?.play()
                                binding.tvStatus.text = "Tracking: $name"
                            } else {
                                attachVideoNode(image)
                            }
                        }
                    }
                    newState == TrackingState.PAUSED && prevState == TrackingState.TRACKING -> {
                        activePoolItems[name]?.player?.pause()
                        binding.tvStatus.text = "Paused: $name"
                    }
                    newState == TrackingState.STOPPED -> cleanupImage(name)
                }
                lastTrackingState[name] = newState
            }
        }
    }

    private fun attachVideoNode(image: AugmentedImage) {
        val name = image.name
        val config = videoConfig[name] ?: return
        val poolItem = playerPool.acquire() ?: return
        val player = poolItem.player

        val targetMedia = "asset:///${config.videoPath}"
        if (player.currentMediaItem?.localConfiguration?.uri?.toString() != targetMedia) {
            player.setMediaItem(MediaItem.fromUri(targetMedia))
            player.prepare()
        }
        
        player.playWhenReady = true
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
                }

                anchorNode.addChildNode(planeNode)
                binding.arSceneView.addChildNode(anchorNode)
                activeNodes[name] = anchorNode

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
        if (::playerPool.isInitialized) playerPool.releaseAll()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ARVideo"
        private const val IMAGE_DB_ASSET = "ar_images.imgdb"
        private const val CAMERA_PERMISSION_CODE = 1001
    }
}
