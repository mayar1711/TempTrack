package com.example.temptrack.ui.home.view

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.temptrack.checknetowrk.NetworkConnectivity
import com.example.temptrack.checknetowrk.NetworkStatus
import com.example.temptrack.data.database.DatabaseClient
import com.example.temptrack.data.database.FavoriteLocalDataSourceImo
import com.example.temptrack.data.model.WeatherForecastResponse
import com.example.temptrack.data.model.getCountryName
import com.example.temptrack.data.network.RetrofitClient
import com.example.temptrack.data.network.datasource.WeatherRemoteDataSourceImpl
import com.example.temptrack.data.repositry.WeatherRepositoryImpl
import com.example.temptrack.databinding.FragmentHomeBinding
import com.example.temptrack.datastore.ENUM_LANGUAGE
import com.example.temptrack.datastore.ENUM_LOCATION
import com.example.temptrack.datastore.ENUM_TEMP_PREF
import com.example.temptrack.datastore.SettingDataStorePreferences
import com.example.temptrack.location.LocationStatus
import com.example.temptrack.ui.home.viewmodel.HomeViewModel
import com.example.temptrack.ui.home.viewmodel.HomeViewModelFactory
import com.example.temptrack.ui.map.MapsActivity
import com.example.temptrack.util.ResultCallBack
import com.example.temptrack.util.convertToDailyWeather
import com.example.temptrack.util.convertToHourlyWeather
import com.example.temptrack.util.getImageIcon
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

@Suppress("UNREACHABLE_CODE")
class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var binding: FragmentHomeBinding
    private lateinit var settingSharedPreferences: SettingDataStorePreferences
    private lateinit var adapter: DailyWeatherAdapter
    private lateinit var todayAdapter: HourlyWeatherAdapter
    private  var unit: String="metric"
    private var language:String="en"
    private var _latitude: Double = 0.0
    private var _longitude: Double = 0.0
    private lateinit var networkConnectivity: NetworkConnectivity
    private  var country:String?=" "

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.recyclerForWeek.layoutManager = LinearLayoutManager(requireContext())
        adapter = DailyWeatherAdapter { dailyWeather ->
        }
        binding.recyclerForWeek.adapter = adapter

        todayAdapter = HourlyWeatherAdapter { hourlyWeather ->

        }
        binding.recyclerForToday.adapter = todayAdapter

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        networkConnectivity = NetworkConnectivity.getInstance(requireActivity().application)
        val repository = WeatherRepositoryImpl.getInstance(
            WeatherRemoteDataSourceImpl.getInstance(RetrofitClient.weatherApiService),
            FavoriteLocalDataSourceImo.getInstance(DatabaseClient.getInstance(requireContext()).favoriteDao())
        )
        val factory = HomeViewModelFactory(requireActivity().application, repository)
        settingSharedPreferences = SettingDataStorePreferences.getInstance(requireContext())

        viewModel = ViewModelProvider(this, factory).get(HomeViewModel::class.java)
        binding.locationImage.setOnClickListener {
            val intent = Intent(requireContext(), MapsActivity::class.java)
            val bundle = Bundle().apply {
                putString("fragment_name", "HomeFragment")
            }
            intent.putExtras(bundle)
            startActivity(intent)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tempPref = settingSharedPreferences.getTempPreference().firstOrNull()
                val langPref = settingSharedPreferences.getLangPreference().firstOrNull()
                val locationPref = settingSharedPreferences.getLocationPreference().first()

                unit = when (tempPref) {
                    ENUM_TEMP_PREF.CELSIUS -> "metric"
                    ENUM_TEMP_PREF.FAHRENHEIT-> "imperial"
                    ENUM_TEMP_PREF.KELVIN-> "standard"
                    else -> "metric"
                }
                Log.d("HomeFragment", "onViewCreated: $unit")

                language = when (langPref) {
                    ENUM_LANGUAGE.ENGLISH -> "en"
                    ENUM_LANGUAGE.ARABIC -> "ar"
                    else -> "en"
                }
                Log.d("HomeFragment", "onViewCreated: $language")

                when (locationPref) {
                    ENUM_LOCATION.MAP -> {
                        binding.locationImage.visibility=View.VISIBLE
                        Log.d("HomeFragment", "onViewCreated: MAP")
                         _latitude= settingSharedPreferences.getLatitude().firstOrNull()!!
                        _longitude= settingSharedPreferences.getLongitude().firstOrNull()!!
                        val geocoder=Geocoder(requireContext(),Locale.getDefault())
                        country= geocoder.getFromLocation(_latitude,_longitude,1)?.get(0)?.adminArea
                        Log.i("HomeFragment", "onViewCreated MAP: $_latitude, $_longitude")
                        binding.tvCity.text=country
                    }
                    ENUM_LOCATION.GPS -> {
                         val _location = MutableStateFlow<LocationStatus>(LocationStatus.Loading)
                         val location = _location.asStateFlow()

                         val mFusedLocationProviderClient: FusedLocationProviderClient by lazy {
                            LocationServices.getFusedLocationProviderClient(requireActivity())
                        }
                        val tokenSource = CancellationTokenSource()
                        val token = tokenSource.token
                        if (ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return@launch
                        }
                        mFusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token)
                            .addOnSuccessListener { location ->
                                val latLng = LatLng(location.latitude, location.longitude)
                                val isEmitted = _location.tryEmit(LocationStatus.Success(latLng))
                                Log.d("WeatherLocationManager", "Location request successful: $latLng, Emitted: $isEmitted")
                                _latitude=location.latitude
                                _longitude=location.longitude
                                val geocoder=Geocoder(requireContext(),Locale.getDefault())
                                country= geocoder.getFromLocation(_latitude,_longitude,1)?.get(0)?.adminArea
                                Log.i("TAG", "onViewCreated: $country")
                            }

                            .addOnFailureListener { e ->
                                Log.e("WeatherLocationManager", "Failed to receive GPS location: ${e.message}", e)
                                _location.tryEmit(LocationStatus.Failure(e.message.toString()))
                            }
                        binding.locationImage.visibility=View.GONE

                       /* viewModel.latitude.collect { latitude ->
                            _latitude = latitude

                        viewModel.longitude.collect { longitude ->
                            _longitude = longitude
                            Log.i("TAG", "onViewCreated:gps $_longitude ,$_latitude")

                        }
                    }
*/                       /* val geocoder=Geocoder(requireContext(),Locale.getDefault())
                        country= geocoder.getFromLocation(30.2794938,32.2792794,1)?.get(0)?.adminArea
                        val countryName: String= getCountryName(requireContext(),30.2794938,32.2792794)*/
                    }

                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching preferences: ${e.message}")
            }
        }

        viewModel.fetchWeatherForecast(_latitude, _longitude, unit, language)
        Log.i("TAG", "onViewCreated: _longitude= $_longitude _latitude=,$_latitude")

        fetchDataFromFile()
        viewLifecycleOwner.lifecycleScope.launch {
            networkConnectivity.connectivitySharedFlow.collect { networkStatus ->
                if (networkStatus == NetworkStatus.CONNECTED) {
                    fetchDataFromNetwork()
                } else if (networkStatus==NetworkStatus.LOST){
                   fetchDataFromFile()
                }
            }
        }


        if (checkPermission()) {
            if (isLocationEnabled()) {
                viewModel.requestGPSLocation()

            } else {
                showEnableLocationDialog()
            }
        } else {
            requestPermission()
        }

    }
    private fun fetchDataFromNetwork(){
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.weatherForecast.collect { weatherData ->
                when (weatherData) {
                    is ResultCallBack.Success -> {
                        saveWeatherDataToFile(weatherData.data)
                        visitableTrue()
                        val response=weatherData.data
                        displayData(response)

                    }

                    is ResultCallBack.Error -> {
                        visitableFalse()
                        val errorMessage = weatherData.message
                        Log.i("HomeFragment", "Error fetching weather forecast: $errorMessage")
                    }

                    is ResultCallBack.Loading -> {
                        visitableLoading()

                    }

                }
            }
        }

    }
    private fun readWeatherDataFromFile(): WeatherForecastResponse? {
        val fileName = "weather_data.txt"
        val file = File(requireContext().filesDir, fileName)
        return try {
            if (file.exists()) {
                val jsonString = file.readText()
                val gson = Gson()
                Log.i("HomeFragment", "readWeatherDataFromFile: $jsonString")
                gson.fromJson(jsonString, WeatherForecastResponse::class.java)
            } else {
                Log.e("HomeFragment", "Error: File does not exist")
                null
            }
        } catch (e: IOException) {
            Log.e("HomeFragment", "Error reading weather data from file: IOException", e)
            null
        }
    }

    private fun fetchDataFromFile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val weatherData = readWeatherDataFromFile()
            if (weatherData != null) {
                displayData(weatherData)
            } else {
                Log.i("HomeFragment", "Error: Weather data is null")
            }
        }
    }


    private fun isLocationEnabled(): Boolean {
        val locationManger: LocationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManger.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManger.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            My_LOCATION_PERMISSION_ID
        )
    }
    private fun checkPermission(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) ==
                PackageManager.PERMISSION_GRANTED)
                ||
                (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                        PackageManager.PERMISSION_GRANTED)
    }

    private fun showEnableLocationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Location Services Disabled")
            .setMessage("Please enable location services to use this app.")
            .setPositiveButton("Enable") { dialog, which ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, which ->

            }
            .show()
    }
    companion object{
        private const val My_LOCATION_PERMISSION_ID = 123
    }
    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.cancelCoroutines()

    }

    private fun checkUnit(unit:String):String{
        return when(unit){
            "metric"->"m/s"
            "imperial"->"mm/h"
            "standard"->"m/s"
            else -> "m/s"
        }
    }
    private fun visitableTrue(){
        binding.progressBar.visibility=View.GONE
        binding.iconforNow.visibility=View.VISIBLE
        binding.recyclerForToday.visibility=View.VISIBLE
        binding.recyclerForWeek.visibility=View.VISIBLE
        binding.tvTemp.visibility=View.VISIBLE
        binding.tvCity.visibility=View.VISIBLE
        binding.locationImage.visibility=View.VISIBLE
        binding.detailCard.visibility=View.VISIBLE
        binding.tvDescription.visibility=View.VISIBLE
    }
    private fun visitableFalse(){
        binding.progressBar.visibility=View.GONE
        binding.iconforNow.visibility=View.GONE
        binding.recyclerForToday.visibility=View.GONE
        binding.recyclerForWeek.visibility=View.GONE
        binding.tvTemp.visibility=View.GONE
        binding.tvCity.visibility=View.GONE
        binding.locationImage.visibility=View.GONE
        binding.detailCard.visibility=View.GONE
        binding.tvDescription.visibility=View.GONE
    }
    private fun visitableLoading(){
        binding.progressBar.visibility=View.VISIBLE
        binding.iconforNow.visibility=View.GONE
        binding.recyclerForToday.visibility=View.GONE
        binding.recyclerForWeek.visibility=View.GONE
        binding.tvTemp.visibility=View.GONE
        binding.tvCity.visibility=View.GONE
        binding.locationImage.visibility=View.GONE
        binding.detailCard.visibility=View.GONE
        binding.tvDescription.visibility=View.GONE
    }

    private suspend fun saveWeatherDataToFile(data: WeatherForecastResponse) {
        withContext(Dispatchers.IO) {
            val fileName = "weather_data.txt"
            val file = File(requireContext().filesDir, fileName)

            try {
                val gson = Gson()
                val jsonString = gson.toJson(data)
                file.writeText(jsonString)
            } catch (e: IOException) {
                Log.e("HomeFragment", "Error saving weather data: ${e.message}")
            }
        }
    }

    fun displayData(response: WeatherForecastResponse){
        Log.d("HomeFragment", "Displaying weather data: $response")
        visitableTrue()
        val dailyItem = response.daily
        val data = convertToDailyWeather(dailyItem)
        adapter.submitList(data)
        val hourlyItem = response.hourly
        val homeData = convertToHourlyWeather(hourlyItem)
        todayAdapter.submitList(homeData)
        dailyItem.get(0).weather.get(0).icon
        val icon = getImageIcon(dailyItem.get(0).weather.get(0).icon)
        binding.iconforNow.setImageResource(icon)
        binding.tvCity.text=country
        binding.tvTemp.text = response.current.temp.toString()
        binding.tvDescription.text = dailyItem.get(0).weather.get(0).description
        binding.pressureMeasure.text= buildString {
            append(response.current.pressure.toString())
            append(" pascal")
        }
        binding.cloudMeasure.text=response.current.clouds.toString()
        binding.humidityMeasure.text= buildString {
            append(response.current.humidity.toString())
            append(" %")
        }
        binding.windMeasure.text= buildString {
            append(response.current.windSpeed.toString())
            append(" ")
            append(checkUnit(unit))
        }
        binding.visibilityMeasure.text=response.current.visibility.toString()
        binding.ultraVioMeasure.text=response.current.uvi.toString()
    }
}
