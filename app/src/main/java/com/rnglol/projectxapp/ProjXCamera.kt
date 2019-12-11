package com.rnglol.projectxapp

import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
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

    // camera settings
    private var useFlash: Boolean = false
    private var resolution: Size = Size(480, 480)
    private var useFront: Boolean = false
    private var bestQuality: Boolean = false

    // constructor
    init {
        Log.d(TAG, "Init camera")
        // get viewFinder by it's ID
        viewFinder = mainActivity.findViewById(R.id.view_finder)
        // start camera in a new thread
        viewFinder.post { startCamera() }
    }

    // todo consider putting it into main activity
    // request camera settings from the server
    fun requestSettings(getSettingsUrl: String, androidId: String) {
        try{
            // get fresh android_id from preferences
            val weakRef = WeakReference<MainActivity>(mainActivity)

            // get settings from server
            GetDevSettings(weakRef).execute(getSettingsUrl, androidId)
        } catch (ex: Exception) {
            Log.e(TAG, "Request settings error")
            ex.printStackTrace()
        }

    }

    // set settings from json
    fun setSettings(message: String): Boolean {

        try {
            val json = JSONObject(message)
            // get values from json
            val uFlash = json.getInt("flash") == 1
            val resWidth = json.getInt("res_width")
            val resHeight = json.getInt("res_height")
            val uFront = json.getInt("front") == 1
            val bQual = json.getInt("quality") == 1

            // required resolution
            val reso = Size(resWidth, resHeight)

            // if required setting didn't change, return
            if(useFlash == uFlash  && resolution == reso
                && useFront == uFront && bestQuality == bQual)
                return false

            // update settings
            useFlash = uFlash
            resolution = reso
            useFront = uFront
            bestQuality = bQual

            // make some information noise
            val msg = "Set settings: flash $useFlash, res: ${resolution}, " +
                    "front: $useFront, quality: $bestQuality"
            Log.d(TAG, msg)
            viewFinder.post {
                Toast.makeText(mainActivity.baseContext, msg, Toast.LENGTH_SHORT).show()
            }

            // start camera with new settings
            startCamera()

            return true

        }  catch (ex: JSONException) {
            Log.e(TAG, "Incorrect JSON: $message")
            ex.printStackTrace()
        }   catch (ex: Exception) {
            Log.e(TAG, "Set Settings error")
            ex.printStackTrace()
        }


        return false
    }

    private fun startCamera() {

        Log.d(TAG, "Start camera")

        try {

            // unbind everything we bind to life cycle
            CameraX.unbindAll()

            Log.d(TAG, "unbind")
            // get camera orientation
            val lensFacing = if (useFront)
                CameraX.LensFacing.FRONT
            else
                CameraX.LensFacing.BACK

            // Every time the provided texture view changes, recompute layout
            viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateCameraTransform()
            }

            Log.d(TAG, "add layout")
            // Create configuration object for the viewfinder use case
            val previewConfig = PreviewConfig.Builder().apply {

                // set required resolution for preview
                setTargetResolution(resolution)

                // set required camera orientation for preview
                setLensFacing(lensFacing)
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

            Log.d(TAG, "set listener")

            // Create configuration object for the image capture use case
            val imageCaptureConfig = ImageCaptureConfig.Builder()
                .apply {
                    // we select a capture mode which will infer the appropriate
                    // resolution based on aspect ration and requested mode

                    // set required quality
                    if (bestQuality)
                        setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                    else
                        setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)

                    // set required flash mode
                    if (useFlash)
                        setFlashMode(FlashMode.ON)

                    // set required resolution
                    setTargetResolution(resolution)

                    // set required camera orientation
                    setLensFacing(lensFacing)

                    // todo add capture stages?
                    //setMaxCaptureStages()
                }.build()


            // Build the image capture use case and attach button click listener
            imageCapture = ImageCapture(imageCaptureConfig)
            Log.d(TAG, "image capture config")

            // Bind use cases to lifecycle
            // If Android Studio complains about "this" being not a LifecycleOwner
            // try rebuilding the project or updating the appcompat dependency to
            // version 1.1.0 or higher.
            CameraX.bindToLifecycle(mainActivity, preview, imageCapture)
            Log.d(TAG, "bind to life")

        } catch (ex: Exception) {
            Log.e(TAG, "Camera start error")
            ex.printStackTrace()
        }
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

        try {
            // get internal app dir
            val fileDir: String = mainActivity.filesDir.toString()
            // create file in this dir
            val file = File(fileDir, fileName)

            Log.d(TAG, "Shooting photo: ${fileDir}/${fileName}")

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
                            .execute(
                                fileDir,
                                fileName,
                                fileSendName,
                                sendFileUrl,
                                androidId,
                                time_stamp
                            )

                        viewFinder.post {
                            Toast.makeText(mainActivity.baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
        } catch (ex: Exception) {
            Log.e(TAG, "Shoot and send error")
            ex.printStackTrace()
        }
    }
}
