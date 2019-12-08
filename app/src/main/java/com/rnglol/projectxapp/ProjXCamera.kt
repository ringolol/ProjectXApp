package com.rnglol.projectxapp

import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.*
import java.io.File
import java.util.concurrent.Executors


/* Original code: https://codelabs.developers.google.com/codelabs/camerax-getting-started */


class ProjXCamera// Add this at the end of onCreate function
    (main_act: MainActivity) {

    // tag for logger
    private val TAG = "ProjectX/Camera"

    // CameraX
    // for creating new thread
    private val executor = Executors.newSingleThreadExecutor()
    // View for displaying picture from camera
    private var viewFinder: TextureView
    // Used to capture image from camera
    private var imageCapture: ImageCapture? = null

    // temp file for saving images
    private val fileName: String = "phone.jpg"

    // MainActivity pointer
    private val mainActivity:MainActivity = main_act

    // constructor
    init {
        Log.d(TAG, "Init camera")
        // get viewFinder by it's ID
        viewFinder = mainActivity.findViewById(R.id.view_finder)
        // start camera in a new thread
        viewFinder.post { startCamera() }
    }

    fun setSettings(useFlash: Boolean, resolution: Size, useFront: Boolean, bestQuality: Boolean) {

        Log.d(TAG,"Set settings: flash $useFlash, res: ${resolution}, " +
                "front: $useFront, quality: $bestQuality")

        startCamera(useFlash, resolution, useFront, bestQuality)
    }

    private fun startCamera() {
        startCamera(useFlash = false, resolution = Size(480, 480), useFront = false, bestQuality = false)
    }

    private fun startCamera(useFlash: Boolean, resolution: Size, useFront: Boolean, bestQuality: Boolean) {

        Log.d(TAG, "Start camera")

        CameraX.unbindAll()

        //viewFinder.layoutParams = FrameLayout.LayoutParams(resolution.width,resolution.height)

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateCameraTransform()
        }

        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(resolution)
        }.build()


        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)

            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateCameraTransform()
        }

        // Create configuration object for the image capture use case
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                // We don't set a resolution for image capture; instead, we
                // select a capture mode which will infer the appropriate
                // resolution based on aspect ration and requested mode

                if(bestQuality)
                    setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                else
                    setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)

                if(useFlash)
                    setFlashMode(FlashMode.ON)

                // todo it also changes picture area, fix it
                setTargetResolution(resolution)

                // todo add capture stages?
                //setMaxCaptureStages()

                if(useFront)
                    setLensFacing(CameraX.LensFacing.FRONT)
                else
                    setLensFacing(CameraX.LensFacing.BACK)
            }.build()

        // Build the image capture use case and attach button click listener
        imageCapture = ImageCapture(imageCaptureConfig)

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        CameraX.bindToLifecycle(mainActivity, preview, imageCapture)
    }


    // transform viewFinder image
    private fun updateCameraTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }


    fun shootAndSendPhoto(time_stamp: String, sendFileUrl: String,
                          fileSendName: String, androidId: String) {

        // todo check that texture is ready?
        // get internal app dir
        val fileDir: String = mainActivity.filesDir.toString()
        // create file in this dir
        val file = File(fileDir, fileName)

        Log.d(TAG,"Shooting photo: ${fileDir}/${fileName}")

        // take photo
        imageCapture?.takePicture(file, executor,
            object : ImageCapture.OnImageSavedListener {
                // on error
                override fun onError(
                    imageCaptureError: ImageCapture.ImageCaptureError,
                    message: String,
                    exc: Throwable?
                ) {
                    // yell about error
                    val msg = "Photo capture failed: $message"
                    Log.e(TAG, msg, exc)
                    viewFinder.post {
                        Toast.makeText(mainActivity.baseContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                //on successful image save
                override fun onImageSaved(file: File) {
                    val msg = "Photo captured successfully"
                    Log.d(TAG, msg)

                    // send photo to server in a new thread
                    UploadFileAsync()
                        .execute(fileDir,
                                 fileName,
                                 fileSendName,
                                 sendFileUrl,
                                 androidId,
                                 time_stamp)

                    viewFinder.post {
                        Toast.makeText(mainActivity.baseContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }
}
