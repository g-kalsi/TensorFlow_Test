package com.tf.gurkeerat

import android.Manifest
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.tf.gurkeerat.base.BaseActivity
import com.tf.gurkeerat.databinding.ActivityMainBinding
import com.tf.gurkeerat.utils.ObjectDetectorHelper
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : BaseActivity(),
    ObjectDetectorHelper.DetectorListener,
    OnClickListener,
    SeekBar.OnSeekBarChangeListener {

    private lateinit var viewBinding: ActivityMainBinding
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var captureResults: MutableList<Detection>
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var objectDetectorHelper: ObjectDetectorHelper

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private var cameraFace = CameraSelector.LENS_FACING_BACK

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied, You need camera permissions for this app",
                    Toast.LENGTH_SHORT
                ).show()
                checkCameraPermissions()
            } else {
                setUpCamera()
            }
        }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        checkCameraPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        //initializing object Detector Helper class
        objectDetectorHelper = ObjectDetectorHelper(
            context = this,
            objectDetectorListener = this
        )
        checkCameraPermissions() // checking for the camera permissions
        viewBinding.takePictureIV.setOnClickListener(this) // setting click listeners
        viewBinding.switchCamIV.setOnClickListener(this)
        viewBinding.thresholdSB.setOnSeekBarChangeListener(this) // setting state change listener for seekbar
        //keeping max value 8 and current value 5 which will get convert into 0.8f and 0.5f
        viewBinding.thresholdSB.max = 8
        viewBinding.thresholdSB.progress = 5
    }

    //launching popup for camera permissions
    private fun checkCameraPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    // setting up camera provider to start camera
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                startCamera()
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    // starting up camera with front facing lens by default and analyizing image to get frames for detecting objects
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            // CameraProvider
            val cameraProvider =
                cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

            // CameraSelector - makes assumption that we're only using the back camera
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(cameraFace).build()

            // Preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.cameraPV.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.cameraPV.surfaceProvider)
                }

            imageAnalyzer =
                ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(viewBinding.cameraPV.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    // The analyzer can then be assigned to the instance
                    .also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            if (!::bitmapBuffer.isInitialized) {
                                // The image rotation and RGB image buffer are initialized only once
                                // the analyzer has started running
                                bitmapBuffer = Bitmap.createBitmap(
                                    image.width,
                                    image.height,
                                    Bitmap.Config.ARGB_8888
                                )
                            }

                            detectObjects(image)
                        }
                    }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    companion object {
        private const val TAG = "Gurkeerat_TF_APP: MainActivity"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = viewBinding.cameraPV.display.rotation
    }

    // handling error from the TF object detection
    override fun onError(error: String) {
        this.runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    //handling results from the TF object detection
    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        this.runOnUiThread {
            // Pass necessary information to OverlayView for drawing on the canvas
            viewBinding.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )
            if (results != null) {
                captureResults = results // saving results for the screenshot to draw on canvas
            }
            viewBinding.itemsDetectedTV.text = "Items Detected: ${results?.size}" // displaying count for items detected
            // Force a redraw on each result generated
            viewBinding.overlay.invalidate()
        }
    }

    // handling click handlers
    override fun onClick(p0: View?) {
        when (p0) {
            viewBinding.switchCamIV -> { // click handler for switching camera face
                cameraFace = if (cameraFace == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                startCamera() // implementing the selected settings
            }

            viewBinding.takePictureIV -> {
                // handling rotational angle for the bitmap
                val angle = if (cameraFace == CameraSelector.LENS_FACING_BACK) {
                      90f
                } else { -90f }
                // handling screenshot functionality on capture click
                val bitmap = drawDetectionResult(bitmapBuffer, captureResults, angle) // drawing results on the bitmap
                saveMediaToStorage(bitmap) // saving final generated bitmap into storage
            }
        }
    }

    //Seek bar state change listeners
    override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
        val percentage = (p1/0.08).toInt()
        viewBinding.thresholdValueTV.text = "${percentage}%" // showing percent of threshold user wanted
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {

    }

    override fun onStopTrackingTouch(p0: SeekBar?) {
        objectDetectorHelper.threshold = getConvertedValue(p0!!.progress) // handling seekbar progress according to threshold values
        objectDetectorHelper.clearObjectDetector() // resetting the object detector
        viewBinding.overlay.clear()
    }

}