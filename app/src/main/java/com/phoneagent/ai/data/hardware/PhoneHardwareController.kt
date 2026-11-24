package com.phoneagent.ai.data.hardware

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneHardwareController(private val context: Context) {

    private var isTorchOn = false

    suspend fun setFlashlight(enabled: Boolean): String = withContext(Dispatchers.Default) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return@withContext "Error: CameraManager service unavailable on this device."

            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) {
                return@withContext "Error: No camera hardware detected on device."
            }

            var torchCameraId: String? = null
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (flashAvailable) {
                    torchCameraId = id
                    break
                }
            }

            val targetId = torchCameraId ?: cameraIds.first()
            cameraManager.setTorchMode(targetId, enabled)
            isTorchOn = enabled

            if (enabled) {
                "🔦 Flashlight turned ON (Camera ID: $targetId)"
            } else {
                "🔦 Flashlight turned OFF"
            }
        } catch (e: SecurityException) {
            "Permission denied: Camera permission is required to operate the flashlight: ${e.message}"
        } catch (e: Exception) {
            "Flashlight operation failed: ${e.message ?: "Hardware unavailable"}"
        }
    }

    fun isFlashlightOn(): Boolean = isTorchOn

    var shouldLaunchDialerActivity: Boolean = true

    suspend fun prepareCall(phoneNumber: String): String = withContext(Dispatchers.Main) {
        val cleanNumber = phoneNumber.replace(Regex("""[^0-9+*#]"""), "").trim()
        if (cleanNumber.isBlank()) {
            return@withContext "Error: Invalid phone number provided. Must contain digits."
        }

        try {
            // Use ACTION_DIAL to show the dialer with the number pre-filled.
            // This guarantees human-in-the-loop confirmation before any call is placed!
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (shouldLaunchDialerActivity) {
                context.startActivity(dialIntent)
            }
            "📞 Dialer opened with $cleanNumber. User confirmation required on system dialer to place call."
        } catch (e: Exception) {
            "Failed to open system dialer: ${e.message}"
        }
    }
}
