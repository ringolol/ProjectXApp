package com.rnglol.projectxapp

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*


// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET)

class MainActivity : AppCompatActivity() {

    private val TAG = "ProjectX"
    private var camera: ProjXCamera? = null
    private lateinit var viewFinder: TextureView
    public val android_id = "krakoziabra314"

    // Send data
    private var sendDataTimer = Timer()
    private var sendDataTask: TimerTask? = null
    private val sendInterval: Long = 30000
    val sendFileUrl = "http://31.134.153.18/upload_file.php"
    val sendJsonUrl = "http://31.134.153.18/get_json.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)


        // Request camera permissions
        if (allPermissionsGranted()) {
            camera = ProjXCamera(this)
        } else {
            Log.d(TAG,"Request permission")
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // set-up timer
        class SendDataTask : TimerTask() {
            override fun run() {
                Log.d(TAG,"Sending data")

                // todo send data here
                // shoot and send picture to DB
                camera?.shootAndSendPhoto()
            }
        }
        sendDataTask = SendDataTask()
        sendDataTimer.scheduleAtFixedRate(sendDataTask, 0, sendInterval)

        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
            camera?.shootAndSendPhoto()
            UploadState().execute(sendJsonUrl, "TEST_DATA_STRING")
        }
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                camera = ProjXCamera(this)
            } else {
                Log.e(TAG,"exit app no permission")
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
