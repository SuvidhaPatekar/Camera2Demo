package demo.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.android.synthetic.main.content_main.btnTakePhoto
import kotlinx.android.synthetic.main.content_main.txvCamera
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Arrays

class MainActivity : AppCompatActivity() {
  private val PERMISSIONS_REQUEST_CODE = 200
  private var cameraDevice: CameraDevice? = null
  private lateinit var session: CameraCaptureSession
  private var previewSurface: Surface? = null
  private var jpegCaptureSurface: Surface? = null

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
    setSupportActionBar(toolbar)
    getPermission()

    btnTakePhoto.setOnClickListener {
      capture()
    }
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

  @SuppressLint("MissingPermission")
  private fun setUpCamera() {
    val cameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[1]
    val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
    val streamConfigs: StreamConfigurationMap =
      cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val jpegSizes = streamConfigs.getOutputSizes(ImageFormat.JPEG)

    val jpegImageReader =
      ImageReader.newInstance(jpegSizes[0].width, jpegSizes[0].height, ImageFormat.JPEG, 1)

    jpegImageReader.setOnImageAvailableListener(
        {
          val byteBuffer = it.acquireLatestImage().planes[0].buffer
          val bytes = ByteArray(byteBuffer.remaining())
          byteBuffer.get(bytes)
          var fileOutputStream: FileOutputStream? = null
          try {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .toString();
            val mkDir = File(root + "/DEMO");

            if (!mkDir.exists()) {
              mkDir.mkdirs();
            }

            val iname = "Image-" + System.currentTimeMillis() + ".jpg";
            val file = File(mkDir, iname);

            fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(bytes)
          } catch (e: IOException) {
            e.printStackTrace()
          } finally {
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

    cameraManager.openCamera(cameraId, cameraStateCallback, null)
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
      session.setRepeatingRequest(request.build(), object : CaptureCallback() {}, null)
    }
  }

  private fun capture() {
    val requestCapture = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    requestCapture?.addTarget(jpegCaptureSurface)

    session.capture(requestCapture?.build(), object : CaptureCallback() {}, null)
  }

  private fun showToast(message: Int) {
    Toast.makeText(
        this, message, Toast.LENGTH_SHORT
    )
        .show()
  }
}

