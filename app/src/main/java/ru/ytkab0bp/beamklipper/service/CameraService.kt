package ru.ytkab0bp.beamklipper.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.util.Range
import android.view.Surface
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

@SuppressLint("MissingPermission")
class CameraService : Service() {
    companion object {
        const val ACTION_TOGGLE_FLASHLIGHT = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_FLASHLIGHT"
        const val ACTION_TOGGLE_FOCUS = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_FOCUS"
        const val KEY_FLASHLIGHT = "flashlight"
        const val KEY_AUTOFOCUS = "autofocus"
        const val KEY_FOCUS = "focus"
        private const val TAG = "beam_camera"
        private const val DESCRIPTOR = "ru.ytkab0bp.beamklipper.ICameraService"
        private val PATH_PATTERN = Pattern.compile("GET ([^\\r\\n]+) HTTP/1\\.[0-1]")
        private const val PORT = 8889
        private const val ID = 400000
        private val IO_POOL = Executors.newSingleThreadExecutor()
        private val handlerThreads = CopyOnWriteArrayList<CameraHandlerThread>()
    }

    private var notificationManager: NotificationManager? = null
    private var serverThread: ServerThread? = null
    private lateinit var cameraManager: CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var captureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var openCameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var activeCameraId: String? = null
    private var availabilityCallback: CameraManager.AvailabilityCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val stoppedByUser = java.util.concurrent.atomic.AtomicBoolean(false)

    // A USB UVC webcam shows up through Camera2 as a regular camera ID with
    // LENS_FACING_EXTERNAL once the OS/HAL enumerates it (standard since API 28
    // on devices whose camera HAL implements the external-camera provider).
    // When the user hasn't pinned a specific camera (Prefs.cameraId == null),
    // we prefer that external webcam over the built-in camera for monitoring.
    private fun isExternal(id: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
        } catch (_: CameraAccessException) {
            false
        }
    }

    private fun resolveCameraId(): String? {
        val ids = try { cameraManager.cameraIdList } catch (_: CameraAccessException) { return null }
        if (ids.isEmpty()) return null
        val preferred = Prefs.cameraId
        if (preferred != null && ids.contains(preferred)) return preferred
        if (preferred == null) {
            ids.firstOrNull { isExternal(it) }?.let { return it }
        }
        return ids[0]
    }

    private val serviceBinder = object : Binder() {
        override fun getInterfaceDescriptor(): String = DESCRIPTOR
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE_FLASHLIGHT -> {
                    val flashlight = intent.getBooleanExtra(KEY_FLASHLIGHT, false)
                    Prefs.isFlashlightEnabled = flashlight
                    try {
                        captureRequestBuilder?.set(CaptureRequest.FLASH_MODE,
                            if (flashlight) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to update camera settings", e)
                    }
                }
                ACTION_TOGGLE_FOCUS -> {
                    val autofocus = intent.getBooleanExtra(KEY_AUTOFOCUS, false)
                    Prefs.isAutofocusEnabled = autofocus
                    val focus = intent.getFloatExtra(KEY_FOCUS, 0f)
                    Prefs.focusDistance = focus
                    try {
                        captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
                            if (autofocus) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
                        captureRequestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus)
                        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to update camera settings", e)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        acquireLocks()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
        else
            Notification.Builder(this)
        not.setContentTitle(getString(R.string.CameraTitle))
            .setContentText(getString(R.string.CameraDescription))
            .setSmallIcon(R.drawable.icon_adaptive_foreground)
            .setOngoing(true)
        notificationManager!!.notify(ID, not.build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(ID, not.build())
        }
        return serviceBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_REDELIVER_INTENT

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stoppedByUser.get()) return
    }

    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeamKlipper::CameraWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(10 * 24 * 60 * 60 * 1000L)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wakelock", t)
        }
        try {
            if (wifiLock?.isHeld != true) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL,
                    "BeamKlipper::CameraWiFiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wifilock", t)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {}
        wakeLock = null
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {}
        wifiLock = null
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        acquireLocks()

        cameraThread = HandlerThread("camera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
        cameraHandler?.post { Process.setThreadPriority(-10) }

        serverThread = ServerThread().also { it.start() }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        availabilityCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                // Only auto-switch in "auto" mode (no camera pinned by the user):
                // when a USB webcam is plugged in, prefer it for monitoring.
                if (Prefs.cameraId != null) return
                if (cameraId == activeCameraId) return
                if (!isExternal(cameraId)) return
                cameraHandler?.post {
                    Log.i(TAG, "USB webcam $cameraId attached, switching to it")
                    openSelectedCamera()
                }
            }
        }
        try {
            cameraManager.registerAvailabilityCallback(availabilityCallback!!, cameraHandler)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register camera availability callback", t)
        }
        cameraHandler?.post { openSelectedCamera() }

        val filter = IntentFilter(ACTION_TOGGLE_FLASHLIGHT).apply { addAction(ACTION_TOGGLE_FOCUS) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, KlipperApp.PERMISSION, ViewUtils.getUiHandler(), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter, KlipperApp.PERMISSION, ViewUtils.getUiHandler())
        }
    }

    private fun deliverFrame(data: ByteArray, size: Int, onRelease: () -> Unit) {
        val done = AtomicInteger()
        val total = handlerThreads.size
        for (t in handlerThreads) {
            t.handler.post {
                try {
                    val out = t.out
                    if (!t.oneShot) {
                        out.write("--camera-frame\r\n".toByteArray())
                        out.write("Content-Type: image/jpeg\r\nContent-Length: $size\r\n\r\n".toByteArray())
                    } else {
                        out.write(CameraHandlerThread.snapshotHeaders(size).toByteArray())
                    }
                    out.write(data, 0, size)
                    if (!t.oneShot) {
                        out.write("\r\n\r\n".toByteArray())
                    }
                    out.flush()
                    if (t.oneShot) {
                        t.quit()
                    }
                } catch (e: Exception) {
                    if (t.socket.isClosed) {
                        Log.e(TAG, "Failed to deliver frame", e)
                        t.quit()
                    }
                }
                if (done.incrementAndGet() == total) {
                    onRelease()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSelectedCamera() {
        val id = resolveCameraId()
        if (id == null) {
            Log.e(TAG, "No camera available to open")
            return
        }
        if (id == activeCameraId && openCameraDevice != null) return
        closeActiveCamera()
        activeCameraId = id
        try {
            cameraManager.openCamera(id, CaptureStateCallback(), cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera $id", e)
            activeCameraId = null
        }
    }

    private fun closeActiveCamera() {
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        captureRequestBuilder = null
        try { openCameraDevice?.close() } catch (_: Exception) {}
        openCameraDevice = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    private inner class CaptureStateCallback : CameraDevice.StateCallback() {
        private val bufferStack = java.util.Stack<ByteArray>()
        private var bufferSize = 0

        override fun onOpened(camera: CameraDevice) {
            // openCamera() is async: a rapid hot-plug (webcam attached then
            // immediately detached, or two openSelectedCamera() calls in a row)
            // can let this fire after activeCameraId has already moved on to a
            // different id. Close the now-unwanted device instead of adopting it.
            if (camera.id != activeCameraId) {
                try { camera.close() } catch (_: Exception) {}
                return
            }
            openCameraDevice = camera
            try {
                var width = Prefs.cameraWidth
                var height = Prefs.cameraHeight
                val chars = cameraManager.getCameraCharacteristics(camera.id)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
                if (sizes.isNotEmpty()) {
                    val best = sizes.minByOrNull {
                        val dw = it.width - width
                        val dh = it.height - height
                        dw * dw + dh * dh
                    }
                    if (best != null) {
                        width = best.width
                        height = best.height
                    }
                }
                val targets = ArrayList<Surface>()
                val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 4)
                imageReader = reader
                reader.setOnImageAvailableListener({ r ->
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    if (handlerThreads.isEmpty()) {
                        img.close()
                        return@setOnImageAvailableListener
                    }
                    IO_POOL.submit {
                        val yBuffer = img.planes[0].buffer
                        val uBuffer = img.planes[1].buffer
                        val vBuffer = img.planes[2].buffer

                        val ySize = yBuffer.remaining()
                        val uSize = uBuffer.remaining()
                        val vSize = vBuffer.remaining()

                        val bufSize = ySize + uSize + vSize
                        if (bufferSize < bufSize) {
                            bufferStack.clear()
                            bufferSize = bufSize
                        }
                        val buffer = if (bufferStack.isEmpty()) ByteArray(bufferSize) else bufferStack.pop()

                        yBuffer.get(buffer, 0, ySize)
                        vBuffer.get(buffer, ySize, vSize)
                        uBuffer.get(buffer, ySize + vSize, uSize)

                        val yuvImage = YuvImage(buffer, ImageFormat.NV21, img.width, img.height, null)
                        val conv = ByteArrayOutputStream()
                        // 75 rather than 85: this feed can be relayed through
                        // OctoEverywhere's cloud connection, not just served
                        // over LAN — see the cameraWidth/cameraHeight comment
                        // in Prefs.kt for the measured bandwidth reasoning.
                        yuvImage.compressToJpeg(Rect(0, 0, img.width, img.height), 75, conv)
                        bufferStack.push(buffer)

                        val converted = conv.toByteArray()
                        deliverFrame(converted, converted.size) {}

                        img.close()
                    }
                }, cameraHandler)
                targets.add(reader.surface)
                camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Configured")
                        captureSession = session
                        try {
                            captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            // Not every UVC webcam reports AE target FPS ranges — unlike the
                            // built-in camera path this used to assume one always exists and
                            // crashed the :camera process otherwise. Just skip it if absent.
                            val rangeArray = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                            if (rangeArray != null && rangeArray.isNotEmpty()) {
                                // Fastest range that still caps at a sane
                                // ceiling for a remote-monitoring feed — not
                                // the sensor's absolute max, which can be
                                // 60fps+ and would undo the bandwidth work in
                                // cameraWidth/cameraHeight (Prefs.kt). Falls
                                // back to the slowest available range if
                                // every one exceeds the cap.
                                val targetFpsCap = 30
                                val selectedRange = rangeArray.filter { it.upper <= targetFpsCap }.maxByOrNull { it.upper }
                                    ?: rangeArray.minByOrNull { it.upper }
                                    ?: rangeArray[0]
                                captureRequestBuilder!!.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedRange)
                            }
                            captureRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
                                if (Prefs.isFlashlightEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                            captureRequestBuilder!!.set(CaptureRequest.LENS_FOCUS_DISTANCE, Prefs.focusDistance)
                            captureRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                if (Prefs.isAutofocusEnabled) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
                            captureRequestBuilder!!.addTarget(reader.surface)
                            session.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.d(TAG, "Configure failed")
                    }
                }, cameraHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to configure opened camera ${camera.id}", e)
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Disconnected: ${camera.id}")
            if (camera.id == activeCameraId) {
                closeActiveCamera()
                activeCameraId = null
                cameraHandler?.post { openSelectedCamera() }
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "Error $error on ${camera.id}")
            if (camera.id == activeCameraId) {
                closeActiveCamera()
                activeCameraId = null
                cameraHandler?.post { openSelectedCamera() }
            }
        }
    }

    override fun onDestroy() {
        stoppedByUser.set(true)
        super.onDestroy()
        try { availabilityCallback?.let { cameraManager.unregisterAvailabilityCallback(it) } } catch (_: Throwable) {}
        closeActiveCamera()
        for (h in handlerThreads) h.quit()
        handlerThreads.clear()
        serverThread?.interrupt()
        cameraThread?.quit()
        cameraHandler = null
        stopForeground(true)
        notificationManager?.cancel(ID)
        unregisterReceiver(receiver)
        releaseLocks()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private class ServerThread : Thread() {
        init {
            name = "beam_camera_server"
            isDaemon = true
        }

        override fun run() {
            Process.setThreadPriority(-10)
            try {
                val socket = ServerSocket(PORT)
                while (!isInterrupted) {
                    val sock = socket.accept()
                    CameraHandlerThread(sock)
                }
                socket.close()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private class CameraHandlerThread(sock: Socket) : HandlerThread("beam_camera_handler", -10) {
        companion object {
            // Fluidd/Mainsail's webcam preview fetches the snapshot URL to
            // validate it; a mismatched multipart Content-Type on what's
            // actually a single raw JPEG body (no boundary at all) made that
            // fail even though a plain browser tab or curl -o (which doesn't
            // care about Content-Type) "worked". CORS headers are also needed
            // since the webcam viewer's origin (Fluidd's own port) differs
            // from this server's port.
            private const val CORS_HEADER = "Access-Control-Allow-Origin: *\r\n"
            private const val HEADERS = "HTTP/1.0 200 OK\r\nConnection: close\r\nMax-Age: 0\r\nExpires: 0\r\nCache-Control: no-cache, private\r\nPragma: no-cache\r\n$CORS_HEADER" +
                "Content-Type: multipart/x-mixed-replace; boundary=camera-frame\r\n\r\n"
            fun snapshotHeaders(size: Int) = "HTTP/1.0 200 OK\r\nConnection: close\r\nCache-Control: no-cache, private\r\nPragma: no-cache\r\n$CORS_HEADER" +
                "Content-Type: image/jpeg\r\nContent-Length: $size\r\n\r\n"
        }

        val socket: Socket = sock
        val out: OutputStream = sock.outputStream
        val oneShot: Boolean
        val handler: Handler

        init {
            val input = sock.getInputStream()
            val r = BufferedReader(InputStreamReader(input))
            val line = r.readLine()
            if (line != null) {
                val m = PATH_PATTERN.matcher(line)
                oneShot = m.find() && m.group(1).startsWith("/snapshot")
            } else {
                oneShot = false
            }

            start()
            handler = Handler(looper)
            handler.post {
                try {
                    // The snapshot response's headers need Content-Length, so
                    // they're written once the frame size is known, in
                    // deliverFrame() below — not here.
                    if (!oneShot) {
                        out.write(HEADERS.toByteArray())
                        out.flush()
                    }
                    handlerThreads.add(this@CameraHandlerThread)
                } catch (e: Exception) {
                    Log.e(name, "Failed to write headers", e)
                    quit()
                }
            }
        }

        override fun quit(): Boolean {
            try { socket.close() } catch (_: Exception) {}
            handlerThreads.remove(this@CameraHandlerThread)
            return super.quit()
        }
    }
}
