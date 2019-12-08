package com.rnglol.projectxapp

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.util.*

// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION)

// android_id fragment
class MySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}

//                     TODO LIST
// todo-1 add listener to get camera options from the server
// todo-2 add device registration
// todo-3 if fine location not granted use course location
// todo-4 check if camera is ready when shooting
// todo-5 watch after CameraX updates (it's in alpha faze)
// todo-6 send error messages to server



//                                   MAIN APP SCHEME
//
//
//        ****************************             *****************************
//        *  <Main Activity>         *         --->*  <ProjXCamera>            *
//        *                          *         |   *                           *
//        *  Send Timer              *         |   *  Get photo by CameraX     *
//        *   - Shoot and send photo-*----------   *  Send photo               *
//        *   - Get and send status--*---          *    - UploadFileAsync()----*----------
//        ****************************  |          *****************************         |
//                                      |                                                |
//                                      |                                                |
//        **************************    |          ***********************************   |
//        *  <ProjXDevStatus>      *<----     ---->*  <MultipartUtility>             *<---
//        *                        *          |    *                                 *
//        *  Get Status            *          |    *  Send fields (JSON, value, ...) *
//        *   - Get location       *          |    *  Send files (as byte string)    *
//        *   - Get battery status *          |    ***********************************
//        *  Send Status           *          |
//        *   - UploadState()------*-----------
//        **************************



class MainActivity : AppCompatActivity() {

    // tag for logger
    private val TAG = "ProjectX"

    // camera is used for taking and sending photos
    private var camera: ProjXCamera? = null
    // status is used for getting and sending device statuses
    private var status: ProjXDevStatus? = null

    // device id, which we generate or set for it
    private var androidId: String = ""
    // preferences is used for storing android id
    private lateinit var sharedPreferences: SharedPreferences

    // send data timer settings
    private var sendDataTimer = Timer()
    private var sendDataTask: TimerTask? = null
    private val sendInterval: Long = 30000

    // send URL's
    private val sendFileUrl = "http://31.134.153.18/app_scripts/upload_file.php"
    private val sendJsonUrl = "http://31.134.153.18/app_scripts/get_json.php"
    // image identifier during sending
    private val fileSendName = "sent_image"

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG,"Creating Main Activity")

        // default things
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // if android ID preference is not set
        if(sharedPreferences.getString("android_id", "")  == "") {
            Log.d(TAG,"Android ID is empty, generate android ID")
            // generate android ID
            val uniqueID = UUID.randomUUID().toString()
            // and set android ID preference
            sharedPreferences.edit().putString("android_id", uniqueID).apply()
        }
        // add special element on layout to manipulate android ID preference
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.place_holder, MySettingsFragment())
            .commit()

        // request permissions
        if (allPermissionsGranted()) {
            Log.d(TAG,"All permissions granted")
            // if all permissions granted init camera
            camera = ProjXCamera(this)
        } else {
            Log.d(TAG,"Request permission")
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // init status
        status = ProjXDevStatus(this)

        // set-up timer
        // send data on each tick
        class SendDataTask : TimerTask() {
            override fun run() {
                Log.d(TAG,"Send timer tic")
                sendData()
            }
        }
        // start timer
        sendDataTask = SendDataTask()
        sendDataTimer.scheduleAtFixedRate(sendDataTask, 0, sendInterval)

        // add on click listener to capture button
        // send data on click
        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
            Log.d(TAG,"Capture btn click")
            sendData()
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

    private fun sendData() {
        Log.d(TAG,"Sending data...")

        androidId = sharedPreferences.getString("android_id", "")?:""

        val timeStamp = (System.currentTimeMillis()/1000).toString()
        // shoot and send picture to DB
        camera?.shootAndSendPhoto(timeStamp, sendFileUrl, fileSendName, androidId)

        // get and send status
        status?.getAndSendStatus(timeStamp, sendJsonUrl, androidId)
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG,"resume to app, start location upd")
        // start location updates
        status?.startLocationUpdates()
    }
}
