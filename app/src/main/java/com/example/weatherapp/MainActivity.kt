package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


//import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var mSharedPreferences : SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }

    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this@MainActivity)) {
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val services: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = services.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )
            showProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.i("Error", t.message.toString())
                }

                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()!!

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result", "$weatherList")
                    } else {
                        when (response.code()) {
                            400 -> {
                                Log.i("Error 400", "Bad connection")
                            }

                            404 -> {
                                Log.i("Error 404", "Not found")
                            }

                            else -> {
                                Log.i("Error", "Generic error")
                            }
                        }
                    }
                }
            })

        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = android.net.Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        //setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        //val mLocationRequest =
        //LocationRequest.Builder(1000L).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            val longitude = mLastLocation?.longitude

            Toast.makeText(
                this@MainActivity,
                "Latitude: $latitude Longitude: $longitude",
                Toast.LENGTH_SHORT
            ).show()
            getLocationWeatherDetails(latitude!!, longitude!!)

            mFusedLocationClient.removeLocationUpdates(this)
        }
    }

    private fun isLocationEnabled(): Boolean {

        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }


    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                Log.i("Weather Name", weatherList.weather.toString())

                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainDescription.text =
                    weatherList.weather[i].description
                binding.tvTemp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country

                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

                when(weatherList.weather[i].icon){
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)

                }
            }
        }


    }


    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex : Long) :String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getTimeZone("GMT-4")
        val formattedDate = sdf.format(date)
        return formattedDate
    }

}