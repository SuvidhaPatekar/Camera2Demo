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
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.android.synthetic.main.content_main.txvCamera
import java.util.Arrays

class MainActivity : AppCompatActivity() {
  private val PERMISSIONS_REQUEST_CODE = 200
  private lateinit var mPreviewSurfaceTexture: SurfaceTexture
  private lateinit var mCamera: CameraDevice
  private lateinit var mSession: CameraCaptureSession
  private var previewSurface: Surface? = null
  private var jpegCaptureSurface: Surface? = null
  private var mCaptureResult: TotalCaptureResult? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)
    getPermission()
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
    txvCamera.surfaceTextureListener = object : SurfaceTextureListener {
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
        mPreviewSurfaceTexture = surface
        setUpCamera()
      }
    }
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
          it.acquireLatestImage()
        },
        null
    )
    previewSurface = Surface(mPreviewSurfaceTexture)
    jpegCaptureSurface = jpegImageReader.surface

    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
      override fun onDisconnected(camera: CameraDevice?) {

      }

      override fun onError(
        camera: CameraDevice?,
        error: Int
      ) {
      }

      override fun onOpened(camera: CameraDevice) {
        mCamera = camera

        val surfaces = Arrays.asList(previewSurface!!, jpegCaptureSurface!!)
        captureSurface(surfaces)
      }

    }, null)

  }

  fun captureSurface(surfaces: List<Surface>) {
    mCamera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
      override fun onConfigureFailed(session: CameraCaptureSession?) {

      }

      override fun onConfigured(session: CameraCaptureSession) {
        mSession = session
        startSession()
      }
    }, null)

  }

  fun startSession() {
    val request = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    request.addTarget(previewSurface)

    mSession.setRepeatingRequest(request.build(), object : CaptureCallback() {
      override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
      ) {
        mCaptureResult = result
      }
    }, null)

    val requestCaputre = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    requestCaputre.addTarget(jpegCaptureSurface)

    mSession.capture(requestCaputre.build(), object : CaptureCallback() {
      override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
      ) {
        mCaptureResult = result
        Log.i("mCaptureResult", mCaptureResult.toString())
      }
    }, null)
  }

  private fun showToast(message: Int) {
    val toast = Toast.makeText(
        this, message, Toast.LENGTH_SHORT
    )
    toast.show()
  }
}

