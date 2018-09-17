package demo.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import kotlinx.android.synthetic.main.content_main.btnChangeCamera
import kotlinx.android.synthetic.main.content_main.btnFlash
import kotlinx.android.synthetic.main.content_main.btnTakePhoto
import kotlinx.android.synthetic.main.content_main.tvFps
import kotlinx.android.synthetic.main.content_main.txvCamera
import java.io.File
import java.nio.ByteBuffer
import java.util.Arrays

class MainActivity : AppCompatActivity() {
  private val PERMISSIONS_REQUEST_CODE = 200
  private var cameraDevice: CameraDevice? = null
  private var cameraCaptureSession: CameraCaptureSession? = null
  private var previewSurface: Surface? = null
  private var jpegCaptureSurface: Surface? = null
  private var previewCaptureSurface: Surface? = null
  private lateinit var cameraId: String
  lateinit var cameraCharacteristics: CameraCharacteristics
  private var isFront: Boolean = false
  lateinit var frontCameraId: String
  lateinit var backCameraId: String
  var isFlashOn = false
  var isFLashAvailableFront = false
  var isFlashAvailableBack = false
  var requestCapture: CaptureRequest.Builder? = null
  private lateinit var cameraManager: CameraManager
  private lateinit var frontSize: Size
  private lateinit var backSize: Size
  var handler: Handler? = null
  var handlerThread: HandlerThread? = null
  private val surfaceTextureListener = object : SurfaceTextureListener {
    override fun onSurfaceTextureSizeChanged(
      surface: SurfaceTexture?,
      width: Int,
      height: Int
    ) {
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
      return false
    }

    override fun onSurfaceTextureAvailable(
      surface: SurfaceTexture,
      width: Int,
      height: Int
    ) {
      setUpCamera()
      openCamera()
    }
  }

  private val cameraStateCallback = object : CameraDevice.StateCallback() {
    override fun onDisconnected(camera: CameraDevice) {
      camera.close()
      cameraDevice = null
    }

    override fun onError(
      camera: CameraDevice,
      error: Int
    ) {
      camera.close()
      cameraDevice = null
    }

    override fun onOpened(camera: CameraDevice) {
      cameraDevice = camera
      captureSurface()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    btnTakePhoto.setOnClickListener {
      capture()
    }

    btnChangeCamera.setOnClickListener {
      isFront = !isFront
      closeOperations()
      openCamera()
    }

    btnFlash.setOnClickListener {
      if (isFlashOn) {
        btnFlash.setImageResource(R.drawable.ic_flash_off_black_24dp)
      } else {
        btnFlash.setImageResource(R.drawable.ic_flash_on_black_24dp)
      }
      isFlashOn = !isFlashOn
    }
  }

  override fun onStart() {
    super.onStart()
    getPermission()
    startBackgroundThread()
  }

  override fun onStop() {
    super.onStop()
    closeOperations()
  }

  private fun getPermission() {
    if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
          PERMISSIONS_REQUEST_CODE
      )
    } else {
      startCamera()
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      PERMISSIONS_REQUEST_CODE -> {
        if ((grantResults.isNotEmpty())) {
          if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
          } else {
            showToast(R.string.need_permission, messageString = null)
            getPermission()
          }
        } else {

        }
        return
      }
    }
  }

  private fun startCamera() {
    if (txvCamera.isAvailable) {
      setUpCamera()
      openCamera()
    } else {
      txvCamera.surfaceTextureListener = surfaceTextureListener
    }
  }

  private fun setUpCamera() {
    cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    for (id in cameraManager.cameraIdList) {
      cameraCharacteristics = cameraManager.getCameraCharacteristics(id)

      val cOrientation = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
      val isFlashAvailable = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
      val streamConfigs: StreamConfigurationMap =
        cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
        backCameraId = id
        backSize = streamConfigs.getOutputSizes(ImageFormat.JPEG)[0]
        if (isFlashAvailable) {
          isFlashAvailableBack = true
        }
      } else if (cOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
        frontCameraId = id
        frontSize = streamConfigs.getOutputSizes(ImageFormat.JPEG)[0]
        if (isFlashAvailable) {
          isFLashAvailableFront = true
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun openCamera() {
    val jpegImageReader: ImageReader
    val previewImageReader: ImageReader

    if (isFront) {
      jpegImageReader =
          ImageReader.newInstance(frontSize.width, frontSize.height, ImageFormat.JPEG, 50)
      previewImageReader =
          ImageReader.newInstance(100, 100, ImageFormat.JPEG, 50)
      txvCamera.surfaceTexture.setDefaultBufferSize(frontSize.width, frontSize.height)

      cameraManager.openCamera(frontCameraId, cameraStateCallback, null)
    } else {
      jpegImageReader =
          ImageReader.newInstance(backSize.width, backSize.height, ImageFormat.JPEG, 50)
      previewImageReader =
          ImageReader.newInstance(100, 100, ImageFormat.JPEG, 50)
      txvCamera.surfaceTexture.setDefaultBufferSize(frontSize.width, frontSize.height)
      cameraManager.openCamera(backCameraId, cameraStateCallback, null)
    }

    previewImageReader.setOnImageAvailableListener({
      Log.i("MainActivity", "previewImageReader on image available")
    }, handler)

    jpegImageReader.setOnImageAvailableListener(
        {
          val byteBuffer = it.acquireLatestImage().planes[0].buffer
          createNewImageFile().outputStream()
              .use {
                it.write(getByteArrayFromBuffer(byteBuffer))
                showToast(R.string.save_image, null)
              }
        },
        handler
    )

    previewSurface = Surface(txvCamera.surfaceTexture)
    jpegCaptureSurface = jpegImageReader.surface
    previewCaptureSurface = previewImageReader.surface
  }

  private fun createNewImageFile(): File {
    val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        .toString()
    val mkDir = File("$root/DEMO")

    if (!mkDir.exists()) {
      mkDir.mkdirs()
    }

    val imageName = "Image-" + System.currentTimeMillis() + ".jpg"
    return File(mkDir, imageName)
  }

  private fun getByteArrayFromBuffer(byteBuffer: ByteBuffer): ByteArray {
    val bytes = ByteArray(byteBuffer.remaining())
    byteBuffer.get(bytes)
    return bytes
  }

  fun captureSurface() {
    val surfaces = Arrays.asList(previewSurface!!, jpegCaptureSurface!!)
    cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
      override fun onConfigureFailed(session: CameraCaptureSession?) {

      }

      override fun onConfigured(session: CameraCaptureSession) {
        if (cameraDevice == null) return
        cameraCaptureSession = session
        startSession()
      }
    }, handler)

  }

  fun startSession() {
    try {
      cameraDevice?.let {
        val request = it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        request.addTarget(previewSurface)

        var frames = 0
        var totalFrames = 0
        val initialTime: Long = SystemClock.elapsedRealtimeNanos()

        cameraCaptureSession?.setRepeatingRequest(request.build(), object : CaptureCallback() {
          override fun onCaptureCompleted(
            session: CameraCaptureSession?,
            request: CaptureRequest?,
            result: TotalCaptureResult?
          ) {
            frames++
            totalFrames++
            if (frames % 30 == 0) {
              val currentTime = SystemClock.elapsedRealtimeNanos()
              val fps = Math.round(frames * 1e9 / (currentTime - initialTime))
              //setFps(String.format(getString(R.string.fps), fps))
              showToast(messageString = "fps $fps", message = null)
              frames = 0
            }

            if (totalFrames % 100 == 0) {
              showToast(messageString = "Total frames $totalFrames", message = null)
            }
          }
        }, null)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun setFps(fps: String) {
    tvFps.text = fps
  }

  private fun capture() {
    try {
      requestCapture = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      requestCapture?.addTarget(jpegCaptureSurface)
      checkFlashAvailable()
      cameraCaptureSession?.capture(requestCapture?.build(), object : CaptureCallback() {}, handler)
    } catch (e: Exception) {

    }

  }

  private fun checkFlashAvailable() {
    if (isFront && isFLashAvailableFront) {
      setFlash()
    } else if (!isFront && isFlashAvailableBack) {
      setFlash()
    }
  }

  private fun setFlash() {
    if (isFlashOn) {
      requestCapture?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
    } else {
      requestCapture?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
    }
  }

  private fun closeOperations() {
    try {
      cameraDevice?.close()
      cameraDevice = null
      cameraCaptureSession?.close()
      cameraCaptureSession = null
      stopBackgroundThread()
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    }
  }

  private fun startBackgroundThread() {
    handlerThread = HandlerThread("background thread")
    handlerThread?.start()
    handler = Handler(handlerThread?.looper)
  }

  private fun stopBackgroundThread() {
    handlerThread?.quitSafely()
    try {
      handlerThread?.join()
      handlerThread = null
      handler = null
    } catch (e: InterruptedException) {
      e.printStackTrace()
    }

  }

  private fun showToast(
    message: Int?,
    messageString: String?
  ) {
    message?.let {
      Toast.makeText(
          this, it, Toast.LENGTH_SHORT
      )
          .show()
    }

    messageString?.let {
      Toast.makeText(
          this, it, Toast.LENGTH_SHORT
      )
          .show()
    }
  }
}

