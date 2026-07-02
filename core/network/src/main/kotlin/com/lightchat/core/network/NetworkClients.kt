package com.lightchat.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Process-wide clients. Clients built with newBuilder share OkHttp's dispatcher,
 * connection pool and internal resources while retaining workload-specific timeouts.
 */
object NetworkClients {
    val base: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val http: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        base.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val webSocket: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        base.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    val image: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        base.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
