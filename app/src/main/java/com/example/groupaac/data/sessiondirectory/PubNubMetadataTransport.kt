package com.example.groupaac.data.sessiondirectory

import com.example.groupaac.data.realtime.PubNubRuntimeConfig
import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.v2.PNConfiguration
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Small transport model that isolates PubNub SDK types from the
 * SessionDirectory implementation.
 */
internal data class PubNubMetadataRecord(
    val id: String,
    val custom: Map<String, Any?>,
    val eTag: String?
)

/**
 * Low-level App Context operations needed by the session directory.
 */
internal interface PubNubMetadataTransport {

    /**
     * Returns null when no metadata exists for this exact ID.
     */
    suspend fun get(
        metadataId: String
    ): PubNubMetadataRecord?

    /**
     * Creates or replaces all metadata for this exact ID.
     */
    suspend fun set(
        metadataId: String,
        name: String,
        custom: Map<String, Any?>,
        type: String,
        status: String,
        ifMatchesEtag: String? = null
    ): PubNubMetadataRecord

    suspend fun remove(
        metadataId: String
    )

    suspend fun close()
}

/**
 * Real PubNub App Context implementation.
 *
 * This owns a dedicated PubNub client used only for metadata requests.
 * Realtime subscriptions continue to use RealtimeClientManager.
 */
internal class SdkPubNubMetadataTransport(
    private val pubNub: PubNub
) : PubNubMetadataTransport {

    override suspend fun get(
        metadataId: String
    ): PubNubMetadataRecord? =
        suspendCancellableCoroutine { continuation ->

            pubNub.getChannelMetadata(
                channel = metadataId,
                includeCustom = true
            ).async { result ->

                result.onSuccess { response ->
                    if (!continuation.isActive) {
                        return@onSuccess
                    }

                    val metadata = response?.data

                    if (metadata == null) {
                        continuation.resume(null)
                    } else {
                        continuation.resume(
                            metadata.toTransportRecord()
                        )
                    }
                }.onFailure { error ->
                    if (!continuation.isActive) {
                        return@onFailure
                    }

                    val pubNubError =
                        error as? PubNubException

                    if (pubNubError?.statusCode == 404) {
                        continuation.resume(null)
                    } else {
                        continuation.resumeWithException(error)
                    }
                }
            }
        }

    override suspend fun set(
        metadataId: String,
        name: String,
        custom: Map<String, Any?>,
        type: String,
        status: String,
        ifMatchesEtag: String?
    ): PubNubMetadataRecord =
        suspendCancellableCoroutine { continuation ->

            pubNub.setChannelMetadata(
                channel = metadataId,
                name = name,
                custom = custom,
                includeCustom = true,
                type = type,
                status = status,
                ifMatchesEtag = ifMatchesEtag
            ).async { result ->

                result.onSuccess { response ->
                    if (!continuation.isActive) {
                        return@onSuccess
                    }

                    val metadata = response?.data

                    if (metadata == null) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "PubNub returned no metadata " +
                                        "after setting $metadataId."
                            )
                        )
                    } else {
                        continuation.resume(
                            metadata.toTransportRecord()
                        )
                    }
                }.onFailure { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        }

    override suspend fun remove(
        metadataId: String
    ): Unit =
        suspendCancellableCoroutine { continuation ->

            pubNub.removeChannelMetadata(
                channel = metadataId
            ).async { result ->

                result.onSuccess {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }.onFailure { error ->
                    if (!continuation.isActive) {
                        return@onFailure
                    }

                    val pubNubError =
                        error as? PubNubException

                    /*
                     * Removing something already absent is treated
                     * as an idempotent success.
                     */
                    if (pubNubError?.statusCode == 404) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(error)
                    }
                }
            }
        }

    override suspend fun close() {
        pubNub.destroy()
    }

    private fun PNChannelMetadata.toTransportRecord():
            PubNubMetadataRecord {

        return PubNubMetadataRecord(
            id = id,
            custom = custom?.value.orEmpty(),
            eTag = eTag?.value
        )
    }
}

/**
 * Creates a dedicated App Context client.
 *
 * The random process identity avoids pretending that metadata operations
 * are performed by a participant or facilitator account.
 */
internal fun createPubNubMetadataTransport(
    runtimeConfig: PubNubRuntimeConfig
): PubNubMetadataTransport {

    require(runtimeConfig.isConfigured) {
        "PubNub must be configured before creating metadata transport."
    }

    val metadataClientId =
        "group-aac-directory-${UUID.randomUUID()}"

    val configuration = PNConfiguration.builder(
        UserId(metadataClientId),
        runtimeConfig.subscribeKey
    ) {
        publishKey = runtimeConfig.publishKey
        secure = true
    }.build()

    return SdkPubNubMetadataTransport(
        PubNub.create(configuration)
    )
}