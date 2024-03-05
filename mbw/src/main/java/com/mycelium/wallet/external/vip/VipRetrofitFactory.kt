package com.mycelium.wallet.external.vip

import com.mycelium.wallet.BuildConfig
import com.mycelium.wallet.UserKeysManager
import com.mycelium.wallet.external.DigitalSignatureInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VipRetrofitFactory {
    private companion object {
        const val BASE_URL = ""
    }

    private val userKeyPair = UserKeysManager.userSignKeys

    private val httpClient = OkHttpClient.Builder()
        .apply {
            addInterceptor(DigitalSignatureInterceptor(userKeyPair))
            if (!BuildConfig.DEBUG) return@apply
            addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }.build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("$BASE_URL/api/v1/vip-codes/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    fun createApi(): VipAPI = retrofit.create(VipAPI::class.java)
}
