package com.example.bpsync.network

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/**
 * Backend (GitLab'daki FastAPI) ile bağlantı.
 * JWT token varsa her istekte Authorization header eklenir.
 */
object RetrofitClient {

    private val gson = GsonBuilder()
        .registerTypeAdapter(UserDto::class.java, UserDtoDeserializer())
        .create()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
        AuthTokenProvider.token?.let { token ->
            request.addHeader("Authorization", "Bearer $token")
        }
        request.addHeader("Content-Type", "application/json")
        chain.proceed(request.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(ApiConstants.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val api: BPSyncApi = retrofit.create(BPSyncApi::class.java)
}

/** Backend bazen id'yi sayi gonderebilir; parse hatasinda cokme olmasin diye. */
private class UserDtoDeserializer : JsonDeserializer<UserDto> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserDto {
        val obj = json.asJsonObject
        val id = when {
            obj.has("id") && obj.get("id").isJsonPrimitive -> {
                val p = obj.getAsJsonPrimitive("id")
                if (p.isNumber) p.asLong.toString() else p.asString
            }
            else -> ""
        }
        return UserDto(
            id = id,
            email = obj.get("email")?.asString ?: "",
            name = obj.get("name")?.asString ?: "",
            avatarUrl = obj.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString,
            dateOfBirth = obj.get("date_of_birth")?.takeIf { !it.isJsonNull }?.asString,
            bloodType = obj.get("blood_type")?.takeIf { !it.isJsonNull }?.asString,
            emergencyContact = obj.get("emergency_contact")?.takeIf { !it.isJsonNull }?.asString
        )
    }
}
