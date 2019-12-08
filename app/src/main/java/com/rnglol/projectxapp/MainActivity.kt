package com.rnglol.projectxapp

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
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
import org.json.JSONObject
import java.lang.ref.WeakReference
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



/*                     TODO APP LIST
 todo-1 add listener to get camera options from the server
 todo-2 add device registration
 todo-3 if fine location not granted use course location
 todo-4 check if camera is ready when shooting
 todo-5 watch after CameraX updates (it's in alpha faze)
 todo-6 send error messages to server
 todo-7 think a bit about threads
 todo-8 add send request flag to ProjXCamera and ProjXDevStatus
 todo-9 use web socket?
 todo-10 send images with low resolution
 todo-11 Check edge cases
*/


/*                      TODO WEB LIST
 todo-web-1 Save images as web images
 todo-web-2 Add any devices with strange ID into new_devices table
 todo-web-3 Add registration device page
 todo-web-4 Add registration by email -- use input type mail --
        (+use email server to send mail to user)
 todo-web-5 Check php on SQL injections
 todo-web-6 Check edge cases
 todo-web-7 Change images on locations page (index.php)
 todo-web-8 Page devices output different data, use $_SESSION to store data
 todo-web-9 Add cookies?
*/



/*                      TODO DATABASE
 todo-db-1 Use unique flag?
 todo-db-2 Add default values to all fields
*/



//                                   MAIN APP SCHEME
//
//
//        *****************************            *****************************
//        *  <Main Activity>          *        --->*  <ProjXCamera>            *
//        *                           *        |   *                           *
//        *  Send Timer               *        |   *  Get photo by CameraX     *
//        *   - Shoot and send photo->*---------   *  Send photo               *
//        *   - Get and send status-->*---         *    - UploadFileAsync()--->*----------
//        *****************************  |         *****************************         |
//                                       |                                               |
//                                       |                                               |
//        **************************     |         ***********************************   |
//        *  <ProjXDevStatus>      *<-----    ---->*  <MultipartUtility>             *<---
//        *                        *          |    *                                 *
//        *  Get Status            *          |    *  Send fields (JSON, value, ...) *
//        *   - Get location       *          |    *  Send files (as byte string)    *
//        *   - Get battery status *          |    ***********************************
//        *  Send Status           *          |
//        *   - UploadState()----->*-----------
//        **************************



class MainActivity : AppCompatActivity() {

    // tag for logger
    private val TAG = "ProjectX"

    // camera is used for taking and sending photos
    private var camera: ProjXCamera? = null
    // status is used for getting and sending device statuses
    private var status: ProjXDevStatus? = null

    // device id, which we generate or set for it
    //private var androidId: String = ""
    // preferences is used for storing android id
    private lateinit var sharedPreferences: SharedPreferences

    // send data timer settings
    private var sendDataTimer = Timer()
    private var sendDataTask: TimerTask? = null
    private val sendInterval: Long = 30000

    // send URL's
    private val sendFileUrl = "http://31.134.153.18/app_scripts/upload_file.php"
    private val sendJsonUrl = "http://31.134.153.18/app_scripts/get_json.php"
    private val getSettingsUrl = "http://31.134.153.18/app_scripts/give_settings.php"
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

        // get settings on click
        findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
            Log.d(TAG,"Settings btn click")
            requestSettings()
        }
    }

    private fun requestSettings() {
        // get fresh android_id from preferences
        val weakRef = WeakReference<MainActivity>(this)

        // get settings from server
        GetDevSettings(weakRef).execute(getSettingsUrl, getFreshID())

    }

    fun receiveSettings(sett: String) {
        val message = if(sett=="") "No settings" else sett

        Toast.makeText(this,
            "Settings: $message",
            Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Settings: $message")

        // import settings

        var json = JSONObject(message)
        val flash = json.getInt("flash") == 1
        val res_width = json.getInt("res_width")
        val res_height = json.getInt("res_height")
        val front = json.getInt("front") == 1
        val quality = json.getInt("quality") == 1

        camera?.setSettings(
            flash,
            Size(res_width, res_height),
            front,
            quality)

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

        // get fresh android_id from preferences
        val androidId = getFreshID()

        // get current time stamp
        val timeStamp = (System.currentTimeMillis()/1000).toString()

        // shoot and send picture to DB
        camera?.shootAndSendPhoto(timeStamp, sendFileUrl, fileSendName, androidId)

        // get and send status
        status?.getAndSendStatus(timeStamp, sendJsonUrl, androidId)
    }

    private fun getFreshID(): String {
        return sharedPreferences.getString("android_id", "")?:""
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG,"resume to app, start location upd")

        // start location updates
        status?.startLocationUpdates()
    }
}
