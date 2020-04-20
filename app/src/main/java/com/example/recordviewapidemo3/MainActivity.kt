package com.example.recordviewapidemo3

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle



import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList


class MainActivity :AppCompatActivity() {

    companion object {
        val ORIENTATION = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
        const val REQUEST_CAMERA_PERMISSION_CODE = 200
    }

    var cameraId = ""
    var cameraDevice: CameraDevice? = null
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var captureRequestBuilder: CaptureRequest.Builder
    lateinit var imageDimension: Size
    lateinit var videoSize: Size
    lateinit var previewSize: Size
    lateinit var imageReader: ImageReader

    var dir: File? = null
    var file: File? = null

    var isFlashSupported = false
    var backGroundHandler: Handler? = null
    var backGroundThread: HandlerThread? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        textureView.surfaceTextureListener = surfaceTextureView

        video.setOnClickListener {
            takePhoto()
        }
    }

    override fun onResume() {

        startBackgroundThread()

        super.onResume()
        if (textureView.isAvailable) {
            openCamera(true)
        } else
            textureView.surfaceTextureListener = surfaceTextureView
    }

    private fun startBackgroundThread() {
        backGroundThread = HandlerThread("Camera Backgrond")
        backGroundThread!!.start()
        backGroundHandler = Handler(backGroundThread!!.looper)
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()

    }

    private fun stopBackgroundThread() {
        if (backGroundHandler != null) {
            backGroundThread?.quitSafely()
            backGroundHandler = null
            backGroundThread = null
        }

    }


    private fun openCamera(isImage: Boolean) {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraId = manager.cameraIdList[0]

        val characteristics = manager.getCameraCharacteristics(cameraId)

        if (isImage) {
            val map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(
                    ImageFormat.JPEG
                )
            imageDimension = map[0]
        } else {
            val map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        }



        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), REQUEST_CAMERA_PERMISSION_CODE
            )
        } else
            manager.openCamera(cameraId, stateCallback, null)
    }

    private fun takePhoto() {
        if (cameraDevice == null)
            return
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(cameraDevice!!.id)
            var sizes: Array<Size>

            sizes =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(
                    ImageFormat.JPEG
                )
            var height = 480
            var width = 640
            if (sizes != null && sizes.isNotEmpty()) {
                width = sizes[0].width
                height = sizes[0].height
            }

            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurface = ArrayList<Surface>()
            outputSurface.add(imageReader.surface)
            outputSurface.add(Surface(textureView.surfaceTexture))

            val captureBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(imageReader.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder?.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation))

            val imageListener = ImageReader.OnImageAvailableListener {
                try {
                    val image = it.acquireLatestImage()
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    var output: FileOutputStream? = null
                    try {
                        file = File(getExternalFilesDir(null), "${UUID.randomUUID()}.jpg")
                        output = FileOutputStream(file!!).apply {
                            write(bytes)
                        }
                    } catch (e: Exception) {
                        showToast("${e.message}")
                    } finally {
                        output?.close()
                    }

                } catch (e: Exception) {
                    showToast("${e.message}")
                }
            }

            imageReader.setOnImageAvailableListener(imageListener, backGroundHandler)

            val captureListener = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)

                    showToast("Saved : ${file?.absolutePath}")
                    createCameraPreview()
                }
            }

            cameraDevice?.createCaptureSession(
                outputSurface,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(session: CameraCaptureSession) {}

                    override fun onConfigured(session: CameraCaptureSession) {
                        session.capture(
                            captureBuilder!!.build(),
                            captureListener,
                            backGroundHandler
                        )
                    }

                },
                backGroundHandler
            )

        } catch (e: Exception) {
            showToast("${e.message}")
        }
    }


    private val surfaceTextureView = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {

        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = false

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera(true)
        }

    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }

    }

    private fun createCameraPreview() {
        val texture = textureView.surfaceTexture
        texture.setDefaultBufferSize(imageDimension.width, imageDimension.height)

        val surface = Surface(texture)

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)


        captureRequestBuilder.addTarget(surface)

        cameraDevice?.createCaptureSession(
            mutableListOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {

                }

                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) {
                        return
                    }
                    cameraCaptureSession = session
                    updatePreview()
                }

            },
            null
        )

    }

    private fun updatePreview() {
        if (cameraDevice == null)
            return

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        cameraCaptureSession.setRepeatingRequest(
            captureRequestBuilder.build(),
            null,
            backGroundHandler
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showToast("Permission Denied....")
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
