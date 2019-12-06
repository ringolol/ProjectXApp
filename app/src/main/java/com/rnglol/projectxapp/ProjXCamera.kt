package com.rnglol.projectxapp

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.

import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import java.io.File
import java.util.concurrent.Executors

class ProjXCamera {

    private val TAG = "ProjectX/Camera"

    // CameraX
    private val executor = Executors.newSingleThreadExecutor()
    private var viewFinder: TextureView
    private var imageCapture: ImageCapture? = null

    // temp file
    private val fileName: String = "phone.jpg"

    // MainActivity
    private var mainActivity:MainActivity


    constructor(main_act: MainActivity) {
        mainActivity = main_act
        // Add this at the end of onCreate function

        viewFinder = mainActivity.findViewById(R.id.view_finder)

        viewFinder.post { startCamera() }
    }

    fun startCamera() {

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateCameraTransform()
        }

        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(640, 480))
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
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            }.build()

        // Build the image capture use case and attach button click listener
        imageCapture = ImageCapture(imageCaptureConfig)

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        CameraX.bindToLifecycle(mainActivity, preview, imageCapture)
    }

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

    fun shootAndSendPhoto() {

        val fileDir: String = mainActivity.externalMediaDirs.first().toString()
        val file = File(fileDir, fileName)

        Log.d(TAG,"Shooting photo: ${fileDir}/${fileName}")

        imageCapture?.takePicture(file, executor,
            object : ImageCapture.OnImageSavedListener {
                override fun onError(
                    imageCaptureError: ImageCapture.ImageCaptureError,
                    message: String,
                    exc: Throwable?
                ) {
                    val msg = "Photo capture failed: $message"
                    Log.e(TAG, msg, exc)
                    viewFinder.post {
                        Toast.makeText(mainActivity.baseContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onImageSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Log.d(TAG, msg)
                    // todo send file to DB

                    UploadFileAsync()
                        .execute(fileDir,
                                 fileName,
                                 mainActivity.fileSendName,
                                 mainActivity.sendFileUrl,
                                 mainActivity.androidId)
                    viewFinder.post {
                        Toast.makeText(mainActivity.baseContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }
}
