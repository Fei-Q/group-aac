package com.example.groupaac.data.sessiondirectory

import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus
import com.google.gson.JsonPrimitive
import com.pubnub.api.PubNubException
import kotlinx.coroutines.CancellationException

private const val DIRECTORY_METADATA_TYPE =
    "groupAacSessionDirectoryV1"

private const val KEY_PROTOCOL_VERSION =
    "protocolVersion"

private const val KEY_JOIN_CODE =
    "joinCode"

private const val KEY_SESSION_ID =
    "sessionId"

private const val KEY_SESSION_NAME =
    "sessionName"

private const val KEY_HOST_USER_ID =
    "hostUserId"

private const val KEY_DISPLAY_ID =
    "displayId"

private const val KEY_SESSION_STATUS =
    "sessionStatus"

private const val KEY_DISPLAY_MODE =
    "displayMode"

private const val KEY_CREATED_AT =
    "createdAt"

private const val KEY_ACTUAL_STARTED_AT =
    "actualStartedAt"

private const val KEY_EXPIRES_AT =
    "expiresAt"

/**
 * SessionDirectory backed by PubNub App Context channel metadata.
 *
 * Each short code maps to one exact metadata ID:
 *
 * join.12345678
 */
class PubNubSessionDirectory internal constructor(
    private val transport: PubNubMetadataTransport,
    private val nowProvider: () -> Long =
        System::currentTimeMillis
) : SessionDirectory {

    override suspend fun register(
        entry: SessionDirectoryEntry
    ): RegisterSessionResult {
        return try {
            val normalized = normalizeEntry(entry)
                ?: return RegisterSessionResult.Failure(
                    "Session code must contain exactly eight digits."
                )

            if (
                normalized.protocolVersion !=
                SESSION_DIRECTORY_PROTOCOL_VERSION
            ) {
                return RegisterSessionResult.Failure(
                    "Unsupported directory protocol version: " +
                            normalized.protocolVersion
                )
            }

            if (normalized.status != SessionStatus.LIVE) {
                return RegisterSessionResult.Failure(
                    "Only a live session may be registered."
                )
            }

            val metadataId =
                metadataId(normalized.joinCode)

            val existing =
                transport.get(metadataId)

            if (existing != null) {
                val existingSessionId =
                    existing.custom.scalarString(
                        KEY_SESSION_ID
                    )

                /*
                 * Never overwrite another session's reservation.
                 */
                if (
                    existingSessionId != null &&
                    existingSessionId != normalized.sessionId
                ) {
                    return RegisterSessionResult.CodeTaken
                }

                /*
                 * Malformed existing data is treated conservatively.
                 */
                if (existingSessionId == null) {
                    return RegisterSessionResult.Failure(
                        "The join code is occupied by malformed metadata."
                    )
                }
            }

            transport.set(
                metadataId = metadataId,
                name = normalized.sessionName,
                custom = normalized.toCustomMetadata(),
                type = DIRECTORY_METADATA_TYPE,
                status = normalized.status.name,
                ifMatchesEtag = existing?.eTag
            )

            /*
             * Read back from PubNub rather than trusting the write response.
             */
            val verifiedRecord =
                transport.get(metadataId)
                    ?: return RegisterSessionResult.Failure(
                        "Directory registration could not be verified."
                    )

            val verifiedEntry =
                verifiedRecord.toDirectoryEntry()

            if (
                verifiedEntry.sessionId != normalized.sessionId
            ) {
                return RegisterSessionResult.CodeTaken
            }

            RegisterSessionResult.Registered(
                verifiedEntry
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: PubNubException) {
            /*
             * HTTP 412 means an eTag conditional update lost a race.
             */
            if (error.statusCode == 412) {
                RegisterSessionResult.CodeTaken
            } else {
                RegisterSessionResult.Failure(
                    error.message
                        ?: "Unable to register session code."
                )
            }
        } catch (error: Exception) {
            RegisterSessionResult.Failure(
                error.message
                    ?: "Unable to register session code."
            )
        }
    }

    override suspend fun resolve(
        joinCode: String
    ): ResolveJoinCodeResult {
        return try {
            val digits =
                normalizeJoinCodeOrNull(joinCode)
                    ?: return ResolveJoinCodeResult.InvalidCode

            val record =
                transport.get(metadataId(digits))
                    ?: return ResolveJoinCodeResult.NotFound

            val protocolVersion =
                record.custom.scalarString(
                    KEY_PROTOCOL_VERSION
                )?.toIntOrNull()
                    ?: return ResolveJoinCodeResult.Failure(
                        "Session directory entry has no valid protocol version."
                    )

            if (
                protocolVersion !=
                SESSION_DIRECTORY_PROTOCOL_VERSION
            ) {
                return ResolveJoinCodeResult.UnsupportedVersion(
                    protocolVersion
                )
            }

            val entry =
                record.toDirectoryEntry()

            if (entry.status != SessionStatus.LIVE) {
                return ResolveJoinCodeResult.NotLive
            }

            if (entry.expiresAt <= nowProvider()) {
                return ResolveJoinCodeResult.Expired
            }

            ResolveJoinCodeResult.Found(entry)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ResolveJoinCodeResult.Failure(
                error.message
                    ?: "Unable to resolve session code."
            )
        }
    }

    override suspend fun update(
        entry: SessionDirectoryEntry
    ): UpdateDirectoryEntryResult {
        return try {
            val normalized = normalizeEntry(entry)
                ?: return UpdateDirectoryEntryResult.Failure(
                    "Session code must contain exactly eight digits."
                )

            val metadataId =
                metadataId(normalized.joinCode)

            val existing =
                transport.get(metadataId)
                    ?: return UpdateDirectoryEntryResult.NotFound

            val existingSessionId =
                existing.custom.scalarString(
                    KEY_SESSION_ID
                ) ?: return UpdateDirectoryEntryResult.Failure(
                    "Existing directory entry is malformed."
                )

            if (
                existingSessionId != normalized.sessionId
            ) {
                return UpdateDirectoryEntryResult.SessionMismatch
            }

            transport.set(
                metadataId = metadataId,
                name = normalized.sessionName,
                custom = normalized.toCustomMetadata(),
                type = DIRECTORY_METADATA_TYPE,
                status = normalized.status.name,
                ifMatchesEtag = existing.eTag
            )

            val verified =
                transport.get(metadataId)
                    ?: return UpdateDirectoryEntryResult.NotFound

            val verifiedEntry =
                verified.toDirectoryEntry()

            if (
                verifiedEntry.sessionId !=
                normalized.sessionId
            ) {
                return UpdateDirectoryEntryResult.SessionMismatch
            }

            UpdateDirectoryEntryResult.Updated(
                verifiedEntry
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            UpdateDirectoryEntryResult.Failure(
                error.message
                    ?: "Unable to update session directory."
            )
        }
    }

    override suspend fun remove(
        joinCode: String,
        sessionId: String
    ): RemoveDirectoryEntryResult {
        return try {
            val digits =
                normalizeJoinCodeOrNull(joinCode)
                    ?: return RemoveDirectoryEntryResult.NotFound

            val metadataId =
                metadataId(digits)

            val existing =
                transport.get(metadataId)
                    ?: return RemoveDirectoryEntryResult.NotFound

            val existingSessionId =
                existing.custom.scalarString(
                    KEY_SESSION_ID
                ) ?: return RemoveDirectoryEntryResult.Failure(
                    "Existing directory entry is malformed."
                )

            if (existingSessionId != sessionId) {
                return RemoveDirectoryEntryResult.SessionMismatch
            }

            transport.remove(metadataId)

            RemoveDirectoryEntryResult.Removed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            RemoveDirectoryEntryResult.Failure(
                error.message
                    ?: "Unable to remove session directory entry."
            )
        }
    }

    private fun metadataId(
        rawCode: String
    ): String {
        val digits =
            normalizeJoinCodeOrNull(rawCode)
                ?: error(
                    "Session code must contain exactly eight digits."
                )

        return "join.$digits"
    }

    private fun normalizeEntry(
        entry: SessionDirectoryEntry
    ): SessionDirectoryEntry? {
        val digits =
            normalizeJoinCodeOrNull(entry.joinCode)
                ?: return null

        return entry.copy(
            joinCode = formatJoinCode(digits)
        )
    }
}

private fun SessionDirectoryEntry.toCustomMetadata():
        Map<String, Any?> {

    /*
     * Store scalar strings only.
     *
     * This avoids JSON number-conversion ambiguity when data is returned
     * through Map<String, Any?>.
     */
    return mapOf(
        KEY_PROTOCOL_VERSION to
                protocolVersion.toString(),

        KEY_JOIN_CODE to
                joinCode,

        KEY_SESSION_ID to
                sessionId,

        KEY_SESSION_NAME to
                sessionName,

        KEY_HOST_USER_ID to
                hostUserId,

        KEY_DISPLAY_ID to
                displayId,

        KEY_SESSION_STATUS to
                status.name,

        KEY_DISPLAY_MODE to
                displayMode.name,

        KEY_CREATED_AT to
                createdAt.toString(),

        KEY_ACTUAL_STARTED_AT to
                actualStartedAt.toString(),

        KEY_EXPIRES_AT to
                expiresAt.toString()
    )
}

private fun PubNubMetadataRecord.toDirectoryEntry():
        SessionDirectoryEntry {

    val protocolVersion =
        requiredString(KEY_PROTOCOL_VERSION)
            .toIntOrNull()
            ?: invalidField(KEY_PROTOCOL_VERSION)

    val createdAt =
        requiredString(KEY_CREATED_AT)
            .toLongOrNull()
            ?: invalidField(KEY_CREATED_AT)

    val actualStartedAt =
        requiredString(KEY_ACTUAL_STARTED_AT)
            .toLongOrNull()
            ?: invalidField(KEY_ACTUAL_STARTED_AT)

    val expiresAt =
        requiredString(KEY_EXPIRES_AT)
            .toLongOrNull()
            ?: invalidField(KEY_EXPIRES_AT)

    val status = try {
        SessionStatus.valueOf(
            requiredString(KEY_SESSION_STATUS)
        )
    } catch (error: IllegalArgumentException) {
        invalidField(KEY_SESSION_STATUS)
    }

    val displayMode = try {
        DisplayMode.valueOf(
            requiredString(KEY_DISPLAY_MODE)
        )
    } catch (error: IllegalArgumentException) {
        invalidField(KEY_DISPLAY_MODE)
    }

    return SessionDirectoryEntry(
        protocolVersion = protocolVersion,
        joinCode = formatJoinCode(
            requiredString(KEY_JOIN_CODE)
        ),
        sessionId =
            requiredString(KEY_SESSION_ID),
        sessionName =
            requiredString(KEY_SESSION_NAME),
        hostUserId =
            requiredString(KEY_HOST_USER_ID),
        displayId =
            requiredString(KEY_DISPLAY_ID),
        status = status,
        displayMode = displayMode,
        createdAt = createdAt,
        actualStartedAt = actualStartedAt,
        expiresAt = expiresAt
    )
}

private fun PubNubMetadataRecord.requiredString(
    key: String
): String {
    return custom.scalarString(key)
        ?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException(
            "Session directory field '$key' is missing."
        )
}

private fun Map<String, Any?>.scalarString(
    key: String
): String? {
    return when (val value = this[key]) {
        null -> null
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()

        is JsonPrimitive -> {
            if (value.isString) {
                value.asString
            } else {
                value.toString()
            }
        }

        else -> null
    }
}

private fun invalidField(
    field: String
): Nothing {
    throw IllegalStateException(
        "Session directory field '$field' is invalid."
    )
}