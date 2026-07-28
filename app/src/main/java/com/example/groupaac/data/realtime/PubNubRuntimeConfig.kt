package com.example.groupaac.data.realtime

import com.example.groupaac.BuildConfig

data class PubNubRuntimeConfig(
    val publishKey: String,
    val subscribeKey: String
) {
    val isConfigured: Boolean
        get() = publishKey.isNotBlank() && subscribeKey.isNotBlank()
}

object PubNubConfigProvider {
    fun fromBuildConfig(): PubNubRuntimeConfig {
        return PubNubRuntimeConfig(
            publishKey = BuildConfig.PUBNUB_PUBLISH_KEY,
            subscribeKey = BuildConfig.PUBNUB_SUBSCRIBE_KEY
        )
    }
}
