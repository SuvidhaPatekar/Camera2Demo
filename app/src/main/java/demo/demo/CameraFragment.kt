package demo.demo

import android.annotation.SuppressLint
import android.content.Context.CAMERA_SERVICE
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
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import demo.demo.R.string
import kotlinx.android.synthetic.main.fragment_camera.txvCamera
import java.util.Arrays

class CameraFragment : Fragment() {
  private var listener: OnFragmentInteractionListener? = null
  private lateinit var mPreviewSurfaceTexture: SurfaceTexture
  private lateinit var mCamera: CameraDevice
  private lateinit var mSession: CameraCaptureSession
  private var previewSurface: Surface? = null
  private var jpegCaptureSurface: Surface? = null
  private var mCaptureResult: TotalCaptureResult? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_camera, container, false)
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)
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
    val cameraManager = activity?.getSystemService(CAMERA_SERVICE) as CameraManager
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

        showToast(getString(string.open_camera))

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

// set capture options: fine-tune manual focus, white balance, etc.

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

// set capture options: fine-tune manual focus, white balance, etc.

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

    /* mSession.capture(request.build(), object : CaptureCallback() {
       override fun onCaptureCompleted(
         session: CameraCaptureSession?,
         request: CaptureRequest?,
         result: TotalCaptureResult?
       ) {
         mCaptureResult = result
         Log.i("mCaptureResult", mCaptureResult.toString())
       }
     }, null)*/
  }

  fun saveImage() {

  }

  interface OnFragmentInteractionListener {
    // TODO: Update argument type and name
    fun onFragmentInteraction(uri: Uri)
  }

  private fun showToast(message: String) {
    val toast = Toast.makeText(
        activity, message, Toast.LENGTH_SHORT
    )
    toast.show()
  }

  companion object {
    @JvmStatic fun newInstance() =
      CameraFragment().apply {
        arguments = Bundle().apply {
        }
      }
  }
}
