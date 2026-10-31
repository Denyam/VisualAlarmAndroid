package co.denis.VisualAlarm

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * Flashes the camera LED (torch) on a fixed cadence while the alarm rings.
 *
 * Uses [CameraManager.setTorchMode] so no camera session is opened and no new
 * dependencies are needed. All failures (no flash hardware, camera currently in
 * use by another app, missing CAMERA permission, ...) are swallowed so the alarm
 * keeps ringing even when the torch can't be controlled — flashing is
 * best-effort visual feedback, never a hard requirement.
 *
 * Must be stopped via [stop] when the alarm ends; [stop] always forces the
 * torch off.
 */
class TorchFlasher(context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val handler = Handler(Looper.getMainLooper())

    private var cameraId: String? = null
    private var torchOn = false
    private var running = false

    private val toggleRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            setTorch(!torchOn)
            handler.postDelayed(this, STROBE_HALF_PERIOD_MS)
        }
    }

    /**
     * Starts flashing the torch. Returns true if a flash-equipped camera was
     * found and flashing began, false if torch flashing isn't possible here.
     */
    fun start(): Boolean {
        if (running) return true
        val id = findFlashCameraId() ?: return false
        cameraId = id
        running = true
        handler.post(toggleRunnable)
        return true
    }

    /** Stops flashing and forces the torch off. Safe to call multiple times. */
    fun stop() {
        running = false
        handler.removeCallbacks(toggleRunnable)
        forceTorchOff()
    }

    /** First rear camera with a flash, else any camera with a flash. */
    private fun findFlashCameraId(): String? {
        cameraId?.let { return it }
        val manager = cameraManager ?: return null
        return try {
            val withFlash = manager.cameraIdList.filter { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            withFlash.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
        } catch (e: CameraAccessException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun setTorch(enabled: Boolean) {
        val id = cameraId ?: return
        try {
            cameraManager?.setTorchMode(id, enabled)
            torchOn = enabled
        } catch (e: CameraAccessException) {
            // Another app owns the camera or the device hit a fatal error:
            // keep the loop alive; flashing resumes once the camera is free.
        } catch (e: IllegalStateException) {
            // Camera closed between frames — retry on the next tick.
        } catch (e: SecurityException) {
            // Missing CAMERA permission — give up on torch entirely.
            giveUp()
        } catch (e: IllegalArgumentException) {
            // Torch unavailable for this camera id — give up on torch.
            giveUp()
        }
    }

    /** Permanently stops the flash loop after an unrecoverable torch error. */
    private fun giveUp() {
        running = false
        handler.removeCallbacks(toggleRunnable)
        forceTorchOff()
    }

    /**
     * Best-effort torch shutdown without recursion: [torchOn] is cleared first
     * so a failure here can never re-enter [stop]/[giveUp] and loop forever.
     */
    private fun forceTorchOff() {
        val id = cameraId ?: return
        val wasOn = torchOn
        torchOn = false
        if (!wasOn) return
        try {
            cameraManager?.setTorchMode(id, false)
        } catch (e: CameraAccessException) {
            // Nothing more we can do; the system releases the torch anyway.
        } catch (e: SecurityException) {
            // No permission to turn it off — same as above.
        } catch (e: IllegalArgumentException) {
            // Camera id no longer valid — same as above.
        }
    }
}
