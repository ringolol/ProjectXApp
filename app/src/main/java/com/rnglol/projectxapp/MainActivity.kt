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
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
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
 todo-3 check location for older androids
 todo-5 watch after CameraX updates (it's in alpha faze)
 todo-6 send error messages to server
 todo-7 think a bit about threads
 todo-8 add send request flag to ProjXCamera and ProjXDevStatus
 todo-9 OR use web socket?
 todo-11 Check edge cases
*/


/*                      TODO WEB LIST
 todo-web-1 Save images as web images and sample them
 todo-web-2 Add any devices with strange ID into new_devices table
 todo-web-3 Add registration device page
 todo-web-4 Add registration by email -- use input type mail --
        (+use email server to send mail to user)
 todo-web-5 Check php on SQL injections
 todo-web-6 Check edge cases
 todo-web-9 Add cookies?
 todo-web-10 Add camera settings resolution

 60 sec \
 30 sec  |- timer options
 10 sec_/

 ошибка null object

*/



/*                      TODO DATABASE
 todo-db-1 Forbid equal indexes in some tables
 todo-db-2 Check edge cases
*/




//                                   MAIN APP SCHEME
//
//
//    *****************************            *****************************
//    *  <MainActivity>           *            *  <ProjXCamera>            *
//    *                           *            *                           *
//    *  Get Settings Timer       *            *  GetDevSettings(...)<---->*<---<->----
//    *  - Request Settings------>*----->----->*  - Request Settings       *          |
//    *  Receive Settings         *<----<------*< - Receive Settings       *          |
//    *  Send Timer               *            *  Get photo by CameraX     *          |
//    *  - Shoot and send photo-->*----->----->*  Send photo               *          ^
//    *  - Get and send status--->*---         *  - UploadFileAsync()----->*-->--     |
//    *****************************  |         *****************************    |     V
//                                   |                                          V     |
//                                   V                                          |     |
//                                   |                                          V     |
//    **************************     |          ***********************************   |
//    *  <ProjXDevStatus>      *<-----     ---->*  <MultipartUtility>             *<---
//    *                        *           |    *                                 *
//    *  Get Status            *           |    *  Send fields (JSON, value, ...) *
//    *   - Get location       *           ^    *  Send files (as byte string)    *
//    *   - Get battery status *           |    ***********************************
//    *  Send Status           *           |
//    *   - UploadState()----->*----->------
//    **************************
//



class MainActivity : AppCompatActivity() {

    // tag for logger
    private val TAG = "ProjectX"

    // camera is used for taking and sending photos
    private var camera: ProjXCamera? = null
    // status is used for getting and sending device statuses
    private var status: ProjXDevStatus? = null

    // preferences is used for storing android id
    private lateinit var sharedPreferences: SharedPreferences

    // send data timer settings
    private var sendDataTimer = Timer()
    private var sendDataTask: TimerTask? = null
    private var sendInterval: Long = 60000

    // get settings timer
    private var getSettTimer = Timer()
    private var getSettTask: TimerTask? = null
    private var getSettInterval: Long = 1000

    // send URL's
    private val baseUrl = "http://31.134.153.18"
    private val sendFileUrl = "$baseUrl/app_scripts/upload_file.php"
    private val sendJsonUrl = "$baseUrl/app_scripts/get_json.php"
    private val getSettingsUrl = "$baseUrl/app_scripts/give_settings.php"

    // image identifier during sending
    private val fileSendName = "sent_image"

    // force shoot
    private var dummyForceShoot: Int = -1

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

        startSendTimer(sendInterval)

        startGetSettTimer(getSettInterval)

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

    private fun startSendTimer(interval: Long) {

        try {
            // if task is not null cancel it
            sendDataTask?.cancel()

            // set-up sending data timer
            // send data on each tick
            class SendDataTask : TimerTask() {
                override fun run() {
                    Log.v(TAG,"Send data timer TIC")
                    sendData()
                }
            }
            // start sending data timer
            sendDataTask = SendDataTask()
            sendDataTimer.scheduleAtFixedRate(sendDataTask, 0, interval)
        } catch(ex: Exception) {
            Log.e(TAG,"startSendTimer error")
            ex.printStackTrace()
        }
    }

    private fun startGetSettTimer(interval: Long) {

        try {
            // if task is not null cancel it
            getSettTask?.cancel()

            // set-up get settings timer
            // send data on each tick
            class GetSettTask : TimerTask() {
                override fun run() {
                    Log.v(TAG,"Get settings timer TIC")
                    requestSettings()
                }
            }
            // start get settings timer
            getSettTask = GetSettTask()
            getSettTimer.scheduleAtFixedRate(getSettTask, 0, interval)
        } catch (ex: Exception) {
            Log.e(TAG, "startGetSettTimer error")
        }
    }

    // request camera settings from the server
    private fun requestSettings() {
        camera?.requestSettings(getSettingsUrl, getFreshID())
    }

    // receive camera settings from the server
    fun receiveSettings(sett: String) {
        // set camera settings from json
        try {
            //get new timer interval
            val json = JSONObject(sett)

            val sInter = json.getLong("upd_interval")
            // if interval changed
            if(sInter != sendInterval) {
                // restart timer with new interval
                sendInterval = sInter
                startSendTimer(sendInterval)

                val msg = "New timer interval: ${sendInterval/1000} sec"
                Log.d(TAG, msg)
                Toast.makeText(this,
                    msg,
                    Toast.LENGTH_SHORT).show()
            }

            // upd camera settings
            if(camera?.setSettings(json) == true) {
                // if camera setting changed send new picture to server
                sendData()
            }

            // force shoot if dummy changed
            if(json.getInt("dummy") != dummyForceShoot) {
                if(dummyForceShoot != -1) {
                    sendData()
                }
                dummyForceShoot = json.getInt("dummy")
            }

        }  catch (ex: JSONException) {
            Log.e(TAG, "Incorrect JSON: $sett")
            ex.printStackTrace()
        } catch (ex: Exception) {
            Log.e(TAG, "Set send timer interval error")
            ex.printStackTrace()
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

        try {
            return sharedPreferences.getString("android_id", "")?:""
        } catch (ex: Exception) {
            Log.e(TAG, "Get fresh id error")
            ex.printStackTrace()
        }
        return  "pref_id_error"
    }

    override fun onResume() {

        super.onResume()

        Log.d(TAG,"resume to app, start location upd")

        // start location updates
        status?.startLocationUpdates()
    }
}
