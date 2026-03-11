package com.example.arvideo

import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import com.google.android.filament.Engine
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.VideoMaterial
import kotlin.collections.ArrayDeque

/**
 * ExoPlayerPool
 *
 * Maintains a pool of pre-warmed ExoPlayer + VideoMaterial pairs
 * so that video playback can start almost instantly.
 */
class ExoPlayerPool(
    private val context: Context,
    private val engine: Engine,
    private val materialLoader: MaterialLoader,
    private val poolSize: Int = 4
) {
    data class PlayerInstance(
        val player: ExoPlayer,
        val material: VideoMaterial
    )

    private val available: ArrayDeque<PlayerInstance> = ArrayDeque()
    private val inUse: MutableSet<PlayerInstance>     = mutableSetOf()

    init {
        repeat(poolSize) {
            available.addLast(createInstance())
        }
        Log.d(TAG, "Player pool created with $poolSize instances")
    }

    /**
     * Acquire an idle player instance from the pool.
     */
    fun acquire(): PlayerInstance? {
        val instance = available.removeFirstOrNull() ?: run {
            Log.w(TAG, "Pool exhausted, creating extra instance on-demand")
            createInstance()
        }
        inUse.add(instance)
        return instance
    }

    /**
     * Return an instance to the pool after use.
     */
    fun release(instance: PlayerInstance) {
        if (!inUse.remove(instance)) {
            Log.w(TAG, "release() called on an instance not in inUse set")
        }
        
        // Reset to clean state
        instance.player.stop()
        instance.player.clearMediaItems()
        instance.player.setVideoSurface(null) // Detach from surface
        instance.player.volume = 1f
        instance.player.repeatMode = Player.REPEAT_MODE_ONE
        
        // Re-attach to its material's surface for next use
        instance.player.setVideoSurface(instance.material.surface)
        
        available.addLast(instance)
        Log.d(TAG, "Instance returned to pool. Available: ${available.size}")
    }

    fun releaseAll() {
        available.forEach { 
            it.player.release()
            it.material.destroy()
        }
        inUse.forEach { 
            it.player.release()
            it.material.destroy()
        }
        available.clear()
        inUse.clear()
        Log.d(TAG, "All pool resources released")
    }

    private fun createInstance(): PlayerInstance {
        // --- Ultra Low-latency LoadControl for instant playback ---
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                100,    // Min buffer (Absolute minimum for instant start)
                300,    // Max buffer (Minimal)
                10,     // Buffer for playback (Absolute minimum)
                25      // Buffer for playback after rebuffer (Minimal)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false) // Don't keep back buffer for faster switching
            .setTargetBufferBytes(-1) // Unlimited buffer size for faster loading
            .build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = 1f

        val material = VideoMaterial(engine, materialLoader)
        player.setVideoSurface(material.surface)

        return PlayerInstance(player, material)
    }

    private companion object {
        private const val TAG = "ExoPlayerPool"
    }
}
