/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.placesdemo

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources.NotFoundException
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewStub
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.placesdemo.databinding.AutocompleteAddressActivityBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceTypes
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.android.SphericalUtil.computeDistanceBetween
import java.util.*

/**
 *  Activity for using Place Autocomplete to assist filling out an address form.
 */
class AutocompleteAddressActivity : AppCompatActivity(R.layout.autocomplete_address_activity),
    OnMapReadyCallback {
    private lateinit var mapPanel: View

    private var mapFragment: SupportMapFragment? = null
    private lateinit var coordinates: LatLng
    private var map: GoogleMap? = null
    private var marker: Marker? = null
    private var checkProximity = false
    private lateinit var binding: AutocompleteAddressActivityBinding
    private var deviceLocation: LatLng? = null
    private val acceptedProximity = 150.0
    private var startAutocompleteIntentListener = View.OnClickListener { view: View ->
        view.setOnClickListener(null)
        startAutocompleteIntent()
    }

    // [START maps_solutions_android_autocomplete_define]
    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        binding.autocompleteAddress1.setOnClickListener(startAutocompleteIntentListener)
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                // 検索窓から結果を選択した際の処理
                val place = Autocomplete.getPlaceFromIntent(intent)
                fillInAddress(place)
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Log.i(TAG, "User canceled autocomplete")
        }
    }




    private fun startAutocompleteIntent() {
        // Set the fields to specify which types of place data to
        // return after the user has made a selection.
        val fields = listOf(
            Place.Field.ADDRESS_COMPONENTS,
            Place.Field.LAT_LNG, Place.Field.VIEWPORT
        )

        // Build the autocomplete intent with field, country, and type filters applied
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("JP"))
            .setTypesFilter(listOf(PlaceTypes.ADDRESS))
            .build(this)
        startAutocomplete.launch(intent)
    }
    // [END maps_solutions_android_autocomplete_intent]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // APIの初期化
        val apiKey = BuildConfig.PLACES_API_KEY
        if (apiKey.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_api_key), Toast.LENGTH_LONG).show()
            return
        }

        // PLACE機能の初期化
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        // レイアウトの繋ぎこみ
        binding = AutocompleteAddressActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // 入力欄の処理
        binding.autocompleteAddress1.setOnClickListener(startAutocompleteIntentListener)

        // SUBMITボタンの繋ぎこみ
        val saveButton = findViewById<Button>(R.id.autocomplete_save_button)
        saveButton.setOnClickListener { saveForm() }

        // リセットボタンの繋ぎこみ
        val resetButton = findViewById<Button>(R.id.autocomplete_reset_button)
        resetButton.setOnClickListener { clearForm() }

        //地図の初期化
        coordinates = LatLng(35.6809591,139.7673068)
        showMap(coordinates)
    }

    private fun saveForm() {
        Log.d(TAG, "checkProximity = $checkProximity")
        if (checkProximity) {
            checkLocationPermissions()
        } else {
            Toast.makeText(this, R.string.autocomplete_skipped_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            getAndCompareLocations()
        } else {
            requestPermissionLauncher.launch(
                permission.ACCESS_FINE_LOCATION
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getAndCompareLocations() {
        // TODO: Detect and handle if user has entered or modified the address manually and update
        // the coordinates variable to the Lat/Lng of the manually entered address. May use
        // Geocoding API to convert the manually entered address to a Lat/Lng.
        val enteredLocation = coordinates
        map!!.isMyLocationEnabled = true

        // [START maps_solutions_android_location_get]
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
            .addOnSuccessListener(this) { location: Location? ->
                // Got last known location. In some rare situations this can be null.
                if (location == null) {
                    return@addOnSuccessListener
                }
                deviceLocation = LatLng(location.latitude, location.longitude)
                // [START_EXCLUDE]
                Log.d(TAG, "device location = " + deviceLocation.toString())
                Log.d(TAG, "entered location = $enteredLocation")

                // [START maps_solutions_android_location_distance]
                // Use the computeDistanceBetween function in the Maps SDK for Android Utility Library
                // to use spherical geometry to compute the distance between two Lat/Lng points.
                val distanceInMeters: Double =
                    computeDistanceBetween(deviceLocation, enteredLocation)
                if (distanceInMeters <= acceptedProximity) {
                    Log.d(TAG, "location matched")
                    // TODO: Display UI based on the locations matching
                } else {
                    Log.d(TAG, "location not matched")
                    // TODO: Display UI based on the locations not matching
                }
            }
    }

    private fun fillInAddress(place: Place) {
        // レイアウトの更新
        val components = place.addressComponents
        val address1 = StringBuilder()
        // Get each component of the address from the place details,
        // and then fill-in the corresponding field on the form.
        // Possible AddressComponent types are documented at https://goo.gle/32SJPM1
        if (components != null) {
            for (component in components.asList()) {
                when (component.types[0]) {
                    "street_number" -> {
                        address1.insert(0, component.name)
                    }
                    "route" -> {
                        address1.append(" ")
                        address1.append(component.shortName)
                    }
                }
            }
        }
        binding.autocompleteAddress1.setText(address1.toString())

        // MAPの更新
        showMap(place.latLng as LatLng)
    }

    // [START maps_solutions_android_autocomplete_map_add]
    private fun showMap(latLng: LatLng) {
        coordinates = latLng

        // It isn't possible to set a fragment's id programmatically so we set a tag instead and
        // search for it using that.
        mapFragment =
            supportFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG) as SupportMapFragment?

        // We only create a fragment if it doesn't already exist.
        if (mapFragment == null) {
            mapPanel = (findViewById<View>(R.id.stub_map) as ViewStub).inflate()
            val mapOptions = GoogleMapOptions()
            mapOptions.mapToolbarEnabled(false)

            // To programmatically add the map, we first create a SupportMapFragment.
            mapFragment = SupportMapFragment.newInstance(mapOptions)

            // Then we add it using a FragmentTransaction.
            supportFragmentManager
                .beginTransaction()
                .add(
                    R.id.confirmation_map,
                    mapFragment!!,
                    MAP_FRAGMENT_TAG
                )
                .commit()
            mapFragment!!.getMapAsync(this)
        } else {
            updateMap(coordinates)
        }
    }
    // [END maps_solutions_android_autocomplete_map_add]

    private fun updateMap(latLng: LatLng) {
        marker!!.position = latLng
        map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        if (mapPanel.visibility == View.GONE) {
            mapPanel.visibility = View.VISIBLE
        }
    }

    // [START maps_solutions_android_autocomplete_map_ready]
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a string resource.
            val success = map!!.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.style_json)
            )
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
        map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 15f))
        marker = map!!.addMarker(MarkerOptions().position(coordinates))
    }
    // [END maps_solutions_android_autocomplete_map_ready]

    private fun clearForm() {
        binding.autocompleteAddress1.setText("")
        mapPanel.visibility = View.GONE
        binding.autocompleteAddress1.requestFocus()
    }

    // [START maps_solutions_android_permission_request]
    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Since ACCESS_FINE_LOCATION is the only permission in this sample,
            // run the location comparison task once permission is granted.
            // Otherwise, check which permission is granted.
            getAndCompareLocations()
        } else {
            // Fallback behavior if user denies permission
            Log.d(TAG, "User denied permission")
        }
    }
    // [END maps_solutions_android_permission_request]

    companion object {
        private val TAG = AutocompleteAddressActivity::class.java.simpleName
        private const val MAP_FRAGMENT_TAG = "MAP"
    }
}