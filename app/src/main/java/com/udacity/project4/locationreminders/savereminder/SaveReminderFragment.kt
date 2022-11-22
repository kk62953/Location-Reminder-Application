package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

class SaveReminderFragment : BaseFragment() {
    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    //Check if the device is running Q (API 29) or later
    private val runningQOrLater = android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.Q

    private lateinit var appContext: Context
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var newReminder: ReminderDataItem


    companion object {
        private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 66
        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 67
        private const val REQUEST_TURN_DEVICE_LOCATION_ON = 68
        private const val TAG = "SaveReminderFragment"
        internal const val ACTION_GEOFENCE_EVENT =
            "SaveReminderFragment.action.ACTION_GEOFENCE_EVENT"
        const val GEOFENCE_RADIUS_IN_METERS = 120f

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel

        //Add Geofencing Client
        geofencingClient = LocationServices.getGeofencingClient(appContext)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        binding.saveReminder.setOnClickListener {
            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value ?: ""
            val location = _viewModel.reminderSelectedLocationStr.value ?: ""
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            newReminder = ReminderDataItem(
                title,
                description,
                location,
                latitude,
                longitude
            )
            checkPermssionsforGeoFencing()
        }
    }

    private fun checkPermssionsforGeoFencing() {
        // Check whether the app has the appropriate permissions across Android 10+ and all other Android versions.
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            //Request Permissions
            requestForegroundAndBackgroundLocationPermissions()
        }
    }

    /*
    *  Method to determine whether the app has the appropriate permissions across Android 10+ and all other
    *  Android versions.
    */
    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        //Check if the ACCESS_FINE_LOCATION permission is granted.
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            appContext,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ))
        //If the device is running Q or higher, check that the ACCESS_BACKGROUND_LOCATION permission is granted.
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }

    /* Method to request Permissions*/
    private fun requestForegroundAndBackgroundLocationPermissions() {
        //If the permissions have already been approved return out of the method.
        if (foregroundAndBackgroundLocationPermissionApproved())
            return

        //The permissionsArray contains the permissions that are going to be requested.
        // Initially, add ACCESS_FINE_LOCATION since that will be needed on all API levels.
        var permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val resultCode = when {
            runningQOrLater -> {
                permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }
        Log.d(TAG, "Request foreground only location permission")

        //Request permissions passing  the permissions array and the result code.
        requestPermissions(permissionsArray, resultCode)
    }

    /* Handle Request for permissions */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE && grantResults.size > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            Snackbar.make(
                binding.saveReminder,
                R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
            ).setAction(android.R.string.ok) {
                requestForegroundAndBackgroundLocationPermissions()
            }.show()
        }
    }

    /* Method to check Device Location Setting*/
    private fun checkDeviceLocationSettingsAndStartGeofence(resolve: Boolean = true) {
        //create a LocationRequest, a LocationSettingsRequest Builder.
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        //Get Client Setting
        val settingsClient = LocationServices.getSettingsClient(requireActivity())
        //Check Location Setting
        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())
        //Check if the exception is of type ResolvableApiException and if so,
        // try calling the startResolutionForResult() method in order to prompt the
        // user to turn on device location.
        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                try {
                    startIntentSenderForResult(
                        exception.resolution.intentSender, REQUEST_TURN_DEVICE_LOCATION_ON,
                        null, 0, 0, 0, null
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(TAG, "Error getting location settings resolution: " + sendEx.message)
                }
            }
            //f the exception is not of type ResolvableApiException,
            // present a snackbar that alerts the user that location needs to be enabled
            else {
                Snackbar.make(
                    this.requireView(),
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettingsAndStartGeofence()
                }.show()
            }
        }
        //7. If the locationSettingsResponseTask does complete, check that it is successful,
        // if so you will want to add the geofence.
        locationSettingsResponseTask.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.i("Successful", "$it")
                addNewGeofence()
            }
        }
    }

    /*Method to checks if the user has chosen to accept the permissions. If not, it will ask again.*/
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
            checkDeviceLocationSettingsAndStartGeofence(false)
        }
    }

    /*Create a pending intent and connect it to Broadcast reciever in OnCreate()*/
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun addNewGeofence() {
        if (_viewModel.validateAndSaveReminder(newReminder)) {
            val geofence = Geofence.Builder()
                .setRequestId(newReminder.id)
                .setCircularRegion(
                    newReminder.latitude!!,
                    newReminder.longitude!!,
                    GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()

            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(Geofence.GEOFENCE_TRANSITION_ENTER)
                .addGeofence(geofence)
                .build()

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                addOnSuccessListener {
                    Log.e("Add Geofence", geofence.requestId)
                }
                addOnFailureListener {
                    // Failed to add geofences.
                    Toast.makeText(
                        appContext, R.string.geofences_not_added,
                        Toast.LENGTH_SHORT
                    ).show()
                    if ((it.message != null)) {
                        Log.w(TAG, it.message.toString())
                    }
                }
            }
            _viewModel.onClear()
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appContext = context
    }

    override fun onResume() {
        super.onResume()
        appContext = requireContext()
    }
}
