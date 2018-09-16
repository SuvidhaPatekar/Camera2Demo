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
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import kotlinx.android.synthetic.main.content_main.btnChangeCamera
import kotlinx.android.synthetic.main.content_main.btnFlash
import kotlinx.android.synthetic.main.content_main.btnTakePhoto
import kotlinx.android.synthetic.main.content_main.txvCamera
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Arrays

class MainActivity : AppCompatActivity() {
  private val PERMISSIONS_REQUEST_CODE = 200
  private var cameraDevice: CameraDevice? = null
  private var session: CameraCaptureSession? = null
  private var previewSurface: Surface? = null
  private var jpegCaptureSurface: Surface? = null
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
            showToast(R.string.need_permission)
            getPermission()
          }
        } else {

        }
        return
      }
    }
  }

  private fun startCamera() {
    txvCamera.surfaceTextureListener = surfaceTextureListener
  }

  private fun setUpCamera() {
    cameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
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
    openCamera()
  }

  @SuppressLint("MissingPermission")
  private fun openCamera() {
    val jpegImageReader: ImageReader

    if (isFront) {
      jpegImageReader =
          ImageReader.newInstance(frontSize.width, frontSize.height, ImageFormat.JPEG, 50)
      cameraManager.openCamera(frontCameraId, cameraStateCallback, null)
    } else {
      jpegImageReader =
          ImageReader.newInstance(backSize.width, backSize.height, ImageFormat.JPEG, 50)
      cameraManager.openCamera(backCameraId, cameraStateCallback, null)
    }

    jpegImageReader.setOnImageAvailableListener(
        {
          val byteBuffer = it.acquireLatestImage().planes[0].buffer
          val bytes = ByteArray(byteBuffer.remaining())
          byteBuffer.get(bytes)
          var fileOutputStream: FileOutputStream? = null
          try {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .toString()
            val mkDir = File(root + "/DEMO")

            if (!mkDir.exists()) {
              mkDir.mkdirs()
            }

            val imageName = "Image-" + System.currentTimeMillis() + ".jpg"
            val file = File(mkDir, imageName)

            fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(bytes)
          } catch (e: IOException) {
            e.printStackTrace()
          } finally {
            showToast(R.string.save_image)
            if (fileOutputStream != null) {
              try {
                fileOutputStream.close()
              } catch (e: IOException) {
                e.printStackTrace()
              }
            }
          }

        },
        null
    )

    previewSurface = Surface(txvCamera.surfaceTexture)
    jpegCaptureSurface = jpegImageReader.surface
  }

  fun captureSurface() {
    val surfaces = Arrays.asList(previewSurface!!, jpegCaptureSurface!!)
    cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
      override fun onConfigureFailed(session: CameraCaptureSession?) {

      }

      override fun onConfigured(session: CameraCaptureSession) {
        if (cameraDevice == null) return
        this@MainActivity.session = session
        startSession()
      }
    }, null)

  }

  fun startSession() {
    cameraDevice?.let {
      val request = it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      request.addTarget(previewSurface)
      session?.setRepeatingRequest(request.build(), object : CaptureCallback() {
        override fun onCaptureCompleted(
          session: CameraCaptureSession?,
          request: CaptureRequest?,
          result: TotalCaptureResult?
        ) {
        }
      }, null)
    }
  }

  private fun capture() {
    requestCapture = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    requestCapture?.addTarget(jpegCaptureSurface)
    checkFlashAvailable()
    session?.capture(requestCapture?.build(), object : CaptureCallback() {}, null)
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

  private fun flashOn() {

  }

  private fun closeOperations() {
    try {
      if (null != cameraDevice) {
        cameraDevice!!.close()
        cameraDevice == null
      }

      if (session != null) {
        session!!.close()
        session = null
      }
    } catch (e: CameraAccessException) {

    }
  }

  private fun showToast(message: Int) {
    Toast.makeText(
        this, message, Toast.LENGTH_SHORT
    )
        .show()
  }
}

