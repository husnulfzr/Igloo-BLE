package co.igloohome.igloohome_sdk_demo.server

import co.igloohome.igloohome_sdk_demo.BuildConfig
import co.igloohome.igloohome_sdk_demo.MainActivity
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ServerApi {

    companion object {
        var serverBaseUrl = "https://api.igloodeveloper.co/v2/"
        fun create(
            apiEndPoint: String
        ): ServerApi {
            val logging = HttpLoggingInterceptor()
                .apply {
                    this.level = HttpLoggingInterceptor.Level.BODY
                }

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val newRequest = request.newBuilder()
                        .addHeader("x-igloocompany-apikey", MainActivity.SERVER_API_KEY)
                        .method(request.method(), request.body())
                        .build()
                    chain.proceed(newRequest)
                }
                .addInterceptor(logging)
                .build()

            val gson = GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                .create()

            return Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .baseUrl(apiEndPoint)
                .client(client)
                .build()
                .create(ServerApi::class.java)
        }
    }

    @GET("timezone/{tz_id}")
    fun getTimezoneConfiguration(@Path("tz_id") tzId: String): Single<TimezoneConfiguration>

    @POST("locks")
    fun submitPairingData(@Body payload: PairingDataRequest): Single<PairingDataResponse>

    @POST("locks/{id}/ekeys")
    fun getGuestKey(@Path("id") bluetoothId: String, @Body request: EKeyRequest): Single<JsonObject>

    @DELETE("locks/{lockId}")
    fun deleteLock(@Path("lockId") lockId: String): Completable

    @POST("locks/{lockId}/activitylogs")
    fun submitActivityLog(@Path("lockId") lockId: String, @Body request: ActivityLogRequest): Single<JsonObject>
}