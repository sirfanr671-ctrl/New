package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current: CurrentWeather? = null
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    val temperature_2m: Double = 0.0,
    val relative_humidity_2m: Int = 0,
    val wind_speed_10m: Double = 0.0,
    val weather_code: Int = 0
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
    ): OpenMeteoResponse
}

class WeatherRepository {
    private val api: OpenMeteoApi by lazy {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    suspend fun fetchWeatherSummary(latitude: Double = 17.3850, longitude: Double = 78.4867): Result<String> {
        return try {
            val response = api.getCurrentWeather(latitude, longitude)
            val current = response.current ?: return Result.failure(IllegalStateException("Empty weather payload"))
            val condition = weatherCodeToLabel(current.weather_code)
            Result.success(
                "Current weather is $condition, ${current.temperature_2m}°C, humidity ${current.relative_humidity_2m}%, wind ${current.wind_speed_10m} km/h."
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun weatherCodeToLabel(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2, 3 -> "partly cloudy"
        45, 48 -> "foggy"
        51, 53, 55 -> "light drizzle"
        61, 63, 65 -> "rainy"
        71, 73, 75 -> "snowy"
        80, 81, 82 -> "rain showers"
        95, 96, 99 -> "thunderstorms"
        else -> "pleasant conditions"
    }
}
