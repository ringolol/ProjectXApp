package com.rnglol.projectxapp

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.

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

// todo-1 add listener to get camera options from the server
// todo-2 add device registration
// todo-3 check position listener
// todo-4 check if camera is ready when shooting
// todo-5 watch after CameraX updates (it's in alpha faze)

class MainActivity : AppCompatActivity() {

    private val TAG = "ProjectX"
    private var camera: ProjXCamera? = null
    private var status: ProjXDevStatus? = null
    private lateinit var viewFinder: TextureView

    private var androidId: String = ""
    private lateinit var sharedPreferences: SharedPreferences

    // Send data
    private var sendDataTimer = Timer()
    private var sendDataTask: TimerTask? = null
    private val sendInterval: Long = 30000
    private val sendFileUrl = "http://31.134.153.18/app_scripts/upload_file.php"
    private val sendJsonUrl = "http://31.134.153.18/app_scripts/get_json.php"

    //
    private val fileSendName = "sent_image"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)


        // add preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        if(sharedPreferences.getString("android_id", "")  == "") {
            Log.d(TAG,"SET UUID")
            val uniqueID = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("android_id", uniqueID).apply()
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.place_holder, MySettingsFragment())
            .commit()

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
                sendData()
            }
        }
        sendDataTask = SendDataTask()
        sendDataTimer.scheduleAtFixedRate(sendDataTask, 0, sendInterval)

        status = ProjXDevStatus(this)

        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
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
        Log.d(TAG,"Sending data")

        androidId = sharedPreferences.getString("android_id", "")?:""

        val timeStamp = (System.currentTimeMillis()/1000).toString()
        // shoot and send picture to DB
        camera?.shootAndSendPhoto(timeStamp, sendFileUrl, fileSendName, androidId)

        // get and send status
        status?.getAndSendStatus(timeStamp, sendJsonUrl, androidId)
    }

    override fun onResume() {
        super.onResume()

        // start location updates
        status?.startLocationUpdates()
    }
}
