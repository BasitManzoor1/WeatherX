package com.devorbit.weatherx

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WeatherApi {
    private const val TAG = "WeatherApi"
    private const val APIKEY = "58f9671afea2fd649ae7aabd18f04cac"
    @Volatile private var currLon = 0.0
    @Volatile private var currLat = 0.0
    
    private val jsonConfig = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true 
    }

    private fun getErrorMessage(code: Int): String = when (code) {
        401 -> "Invalid API Key"
        404 -> "City Not Found"
        429 -> "Rate Limit Exceeded"
        else -> "Server Error ($code)"
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WeatherXApp/1.0")
            setRequestProperty("Accept", "application/json")
        }
    }

    suspend fun readMainData(city: String): HomeJsonData = withContext(Dispatchers.IO) {
        if (city.isBlank()) return@withContext dummyData("Enter City")
        try {
            val encodedCity = URLEncoder.encode(city.trim(), "UTF-8")
            val url = "https://api.openweathermap.org/data/2.5/weather?q=$encodedCity&units=metric&appid=$APIKEY"
            return@withContext fetchHomeData(url)
        } catch (e: Exception) {
            Log.e(TAG, "MainData Exception", e)
            return@withContext dummyData("Offline: Check Connection")
        }
    }

    suspend fun readMainDataByCoords(lat: Double, lon: Double): HomeJsonData = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$APIKEY"
            return@withContext fetchHomeData(url)
        } catch (e: Exception) {
            Log.e(TAG, "MainDataCoords Exception", e)
            return@withContext dummyData("Location Error")
        }
    }

    private fun fetchHomeData(url: String): HomeJsonData {
        val conn = openConnection(url)
        val responseCode = conn.responseCode
        return if (responseCode == 200) {
            val str = conn.inputStream.bufferedReader().use { it.readText() }
            val data = jsonConfig.decodeFromString<HomeJsonData>(str)
            currLon = data.coord.lon
            currLat = data.coord.lat
            data
        } else {
            Log.e(TAG, "MainData Error: $responseCode")
            dummyData(getErrorMessage(responseCode))
        }
    }

    suspend fun readForecastData(city: String): ForecastJsonData = withContext(Dispatchers.IO) {
        if (city.isBlank()) return@withContext forecastDummyData()
        try {
            val encodedCity = URLEncoder.encode(city.trim(), "UTF-8")
            val url = "https://api.openweathermap.org/data/2.5/forecast?q=$encodedCity&units=metric&appid=$APIKEY"
            return@withContext fetchForecastData(url)
        } catch (e: Exception) {
            Log.e(TAG, "Forecast Exception", e)
            return@withContext forecastDummyData()
        }
    }

    suspend fun readForecastDataByCoords(lat: Double, lon: Double): ForecastJsonData = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&units=metric&appid=$APIKEY"
            return@withContext fetchForecastData(url)
        } catch (e: Exception) {
            Log.e(TAG, "ForecastCoords Exception", e)
            return@withContext forecastDummyData()
        }
    }

    private fun fetchForecastData(url: String): ForecastJsonData {
        val conn = openConnection(url)
        return if (conn.responseCode == 200) {
            val str = conn.inputStream.bufferedReader().use { it.readText() }
            jsonConfig.decodeFromString<ForecastJsonData>(str)
        } else {
            Log.e(TAG, "Forecast Error: ${conn.responseCode}")
            forecastDummyData()
        }
    }

    suspend fun readAQIData(): AQIResponse = withContext(Dispatchers.IO) {
        if (currLat == 0.0 && currLon == 0.0) return@withContext dummyDataAQI()
        try {
            val url = "https://api.openweathermap.org/data/2.5/air_pollution?lat=$currLat&lon=$currLon&appid=$APIKEY"
            val conn = openConnection(url)
            
            if (conn.responseCode == 200) {
                val str = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext jsonConfig.decodeFromString<AQIResponse>(str)
            } else {
                Log.e(TAG, "AQI Error: ${conn.responseCode}")
                return@withContext dummyDataAQI()
            }
        } catch (e: Exception) {
            Log.e(TAG, "AQI Exception", e)
            return@withContext dummyDataAQI()
        }
    }

    fun dummyData(status: String = "Loading") = HomeJsonData(
        coord = Coords(0.0, 0.0),
        weather = listOf(WeatherData(0, "", status, "01d")),
        main = MainData(),
        visibility = 0,
        wind = WindData(),
        clouds = CloudsData(0),
        dt = (System.currentTimeMillis() / 1000).toInt(),
        sys = SysData(),
        timezone = 0,
        id = 0,
        name = status,
        cod = if (status == "Loading") 0 else -1
    )

    fun forecastDummyData() = ForecastJsonData(emptyList(), CityData(0, "", "", 0, 0, 0))
    fun dummyDataAQI() = AQIResponse(listOf(AQIList(AQIMain(0), AQIComponents(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0))))

    @Serializable data class HomeJsonData(val coord: Coords, val weather: List<WeatherData>, val main: MainData, val visibility: Int = 0, val wind: WindData, val clouds: CloudsData, val dt: Int, val sys: SysData, val timezone: Int, val id: Int, val name: String, val cod: Int)
    @Serializable data class Coords(val lon: Double, val lat: Double)
    @Serializable data class WeatherData(val id: Int, val main: String, val description: String, val icon: String)
    @Serializable data class MainData(val temp: Double = 0.0, @SerialName("feels_like") val feelsLike: Double = 0.0, @SerialName("temp_min") val tempMin: Double = 0.0, @SerialName("temp_max") val tempMax: Double = 0.0, val pressure: Int = 0, val humidity: Int = 0)
    @Serializable data class WindData(val speed: Double = 0.0, val deg: Int = 0)
    @Serializable data class SysData(val country: String = "", val sunrise: Int = 0, val sunset: Int = 0)
    @Serializable data class CloudsData(val all: Int)
    @Serializable data class ForecastJsonData(val list: List<ListData>, val city: CityData)
    @Serializable data class ListData(val dt: Int, val main: MainData, val weather: List<WeatherData>, val clouds: CloudData, val wind: WindData, val visibility: Int = 0, val pop: Double = 0.0, @SerialName("dt_txt") val dtTxt: String = "")
    @Serializable data class CloudData(val all: Double)
    @Serializable data class CityData(val id: Int, val name: String, val country: String, val timezone: Int, val sunrise: Int, val sunset: Int)
    @Serializable data class AQIResponse(@SerialName("list") val list: List<AQIList>)
    @Serializable data class AQIList(val main: AQIMain, val components: AQIComponents)
    @Serializable data class AQIMain(val aqi: Int)
    @Serializable data class AQIComponents(val co: Double, val no: Double, val no2: Double, val o3: Double, val so2: Double, @SerialName("pm2_5") val pm25: Double, val pm10: Double, val nh3: Double)
}
