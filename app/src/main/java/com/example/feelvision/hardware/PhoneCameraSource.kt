package com.feelvision.hardware

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.feelvision.di.ApplicationScope
import com.feelvision.domain.model.ButtonEvent
import com.feelvision.domain.model.PhysicalButton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class PhoneCameraSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope   private val scope: CoroutineScope
) : HardwareSource {

    private val TAG = "PhoneCameraSource"

    // ── HardwareSource state ──────────────────────────────────────────

    private val _isReady         = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Phone Camera — Debug Mode")
    override val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _rawLogFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val rawLogFlow: SharedFlow<String> = _rawLogFlow.asSharedFlow()

    private val _buttonEvents = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 32)
    override val buttonEvents: SharedFlow<ButtonEvent> = _buttonEvents.asSharedFlow()

    private val _frameFlow = MutableSharedFlow<Bitmap>(extraBufferCapacity = 8)
    override val frameFlow: SharedFlow<Bitmap> = _frameFlow.asSharedFlow()

    // ── CameraX internals ─────────────────────────────────────────────

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewUseCase: Preview? = null

    override suspend fun initialize() {
        log("[OK] PhoneCameraSource ready")
        _isReady.value = true
    }

    fun bindCamera(owner: LifecycleOwner, preview: Preview) {
        lifecycleOwner  = owner
        previewUseCase  = preview
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                runCatching {
                    cameraProvider = future.get()
                    bindUseCases()
                }.onFailure { log("[ERR] Camera bind: ${it.message}") }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun bindUseCases() {
        val owner    = lifecycleOwner ?: return
        val provider = cameraProvider  ?: return

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also {
                it.setAnalyzer(cameraExecutor) { proxy ->
                    val bitmap = proxy.toBitmap()
                    proxy.close()
                    scope.launch { _frameFlow.emit(bitmap) }
                }
            }

        provider.unbindAll()
        val useCases = buildList {
            previewUseCase?.let { add(it) }
            add(imageCapture!!)
            add(analysis)
        }
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
        log("[OK] Camera bound — back lens active")
    }

    override suspend fun captureNow(): Bitmap? {
        val capture = imageCapture ?: return null.also { log("[ERR] imageCapture null") }
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = image.toBitmap()
                    image.close()
                    log("[OK] Captured ${bmp.width}×${bmp.height}")
                    cont.resume(bmp) {}
                }
                override fun onError(e: ImageCaptureException) {
                    log("[ERR] Capture failed: ${e.message}")
                    cont.resume(null) {}
                }
            })
        }
    }

    override suspend fun sendCommand(command: HardwareCommand) {
        log("[CMD] ${command::class.simpleName} (phone mode — no-op)")
    }

    fun simulateButtonPress(button: PhysicalButton, type: String = "short") {
        val event = when (type) {
            "long"   -> ButtonEvent.LongPress(button)
            "double" -> ButtonEvent.DoubleTap(button)
            "hold5"  -> ButtonEvent.Hold5s(button)
            else     -> ButtonEvent.ShortPress(button)
        }
        scope.launch {
            _buttonEvents.emit(event)
            log("[BTN] Simulated $type press on $button")
        }
    }

    override fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        _isReady.value = false
        log("[OK] Released")
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        _rawLogFlow.tryEmit(msg)
    }
}