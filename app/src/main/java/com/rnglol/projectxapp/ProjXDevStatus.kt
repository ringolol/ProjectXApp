package com.rnglol.projectxapp

import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.location.Location
import android.os.BatteryManager
import android.os.Looper
import android.util.Log
import android.widget.EditText
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import org.json.JSONObject

class ProjXDevStatus {
    private val TAG = "ProjectX/Status"

    // gps
    private var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    var latitude = 0.0
    var longitude = 0.0

    var batteryPct = 0.0
    var chargeStatus = "NONE"

    // MainActivity
    private var mainActivity: MainActivity

    constructor(main_act: MainActivity) {
        mainActivity = main_act
        // init gps
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mainActivity)
    }

    /*private fun getLastLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location ->
                latitude=location.latitude
                longitude=location.longitude
            }
            .addOnFailureListener {
                Log.e(TAG, "Error GPS")
            }
    }*/

    fun prepareBatteryStatus() {
        // battery charging status
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            mainActivity.registerReceiver(null, ifilter)
        }
        val chStatus: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = chStatus == BatteryManager.BATTERY_STATUS_CHARGING
                || chStatus == BatteryManager.BATTERY_STATUS_FULL

        if(isCharging) {
            chargeStatus = "Charging"
        } else {
            chargeStatus = "Discharging"
        }

        // battery level
        if(batteryStatus != null) {
            batteryPct = batteryStatus.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toDouble()
            }
        }
    }

    // set-up location request
    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            // intervals of location upd
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(mainActivity)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        // add location update listener
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return

                for (location in locationResult.locations){
                    latitude = location.latitude
                    longitude = location.longitude
                }
            }
        }
    }

    fun startLocationUpdates() {
        // set-up location request
        createLocationRequest()
        fusedLocationClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }

    fun getAndSendStatus(time_stamp: String, sendJsonUrl: String, androidId: String) {
        prepareBatteryStatus()
        var jsonPos = JSONObject()
        jsonPos.put("time_stamp",time_stamp)
        jsonPos.put("android_id",androidId)
        jsonPos.put("latitude",latitude)
        jsonPos.put("longitude",longitude)
        jsonPos.put("charge_level",batteryPct)
        jsonPos.put("charge_status",chargeStatus)
        UploadState().execute(sendJsonUrl, androidId, jsonPos.toString())
    }
}