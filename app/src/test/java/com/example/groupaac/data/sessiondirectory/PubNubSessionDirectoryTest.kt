package com.example.groupaac.data.sessiondirectory

import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PubNubSessionDirectoryTest {

    @Test
    fun registerThenResolveReturnsEntry() = runTest {
        val transport =
            FakePubNubMetadataTransport()

        val directory =
            PubNubSessionDirectory(
                transport = transport,
                nowProvider = { 1_000L }
            )

        val entry =
            liveEntry()

        val registered =
            directory.register(entry)

        assertTrue(
            registered is RegisterSessionResult.Registered
        )

        val resolved =
            directory.resolve("12345678")

        assertTrue(
            resolved is ResolveJoinCodeResult.Found
        )

        resolved as ResolveJoinCodeResult.Found

        assertEquals(
            entry.sessionId,
            resolved.entry.sessionId
        )

        assertEquals(
            "1234-5678",
            resolved.entry.joinCode
        )
    }

    @Test
    fun registrationDoesNotOverwriteAnotherSession() =
        runTest {

            val transport =
                FakePubNubMetadataTransport()

            val directory =
                PubNubSessionDirectory(
                    transport = transport,
                    nowProvider = { 1_000L }
                )

            directory.register(
                liveEntry(sessionId = "session-a")
            )

            val result =
                directory.register(
                    liveEntry(sessionId = "session-b")
                )

            assertEquals(
                RegisterSessionResult.CodeTaken,
                result
            )
        }

    @Test
    fun expiredEntryCannotResolve() = runTest {
        val transport =
            FakePubNubMetadataTransport()

        val directory =
            PubNubSessionDirectory(
                transport = transport,
                nowProvider = { 10_000L }
            )

        directory.register(
            liveEntry(expiresAt = 5_000L)
        )

        val result =
            directory.resolve("1234-5678")

        assertEquals(
            ResolveJoinCodeResult.Expired,
            result
        )
    }

    @Test
    fun nonLiveEntryCannotResolve() = runTest {
        val transport =
            FakePubNubMetadataTransport()

        val directory =
            PubNubSessionDirectory(
                transport = transport,
                nowProvider = { 1_000L }
            )

        /*
         * Directly seed the transport because register() intentionally
         * rejects non-live entries.
         */
        transport.seed(
            metadataId = "join.12345678",
            entry = liveEntry(
                status = SessionStatus.ENDED
            )
        )

        val result =
            directory.resolve("1234-5678")

        assertEquals(
            ResolveJoinCodeResult.NotLive,
            result
        )
    }

    @Test
    fun updateRejectsDifferentSessionId() = runTest {
        val transport =
            FakePubNubMetadataTransport()

        val directory =
            PubNubSessionDirectory(
                transport = transport
            )

        directory.register(
            liveEntry(sessionId = "session-a")
        )

        val result =
            directory.update(
                liveEntry(sessionId = "session-b")
            )

        assertEquals(
            UpdateDirectoryEntryResult.SessionMismatch,
            result
        )
    }

    @Test
    fun removeMakesEntryUnresolvable() = runTest {
        val transport =
            FakePubNubMetadataTransport()

        val directory =
            PubNubSessionDirectory(
                transport = transport
            )

        val entry =
            liveEntry()

        directory.register(entry)

        val removed =
            directory.remove(
                joinCode = entry.joinCode,
                sessionId = entry.sessionId
            )

        assertEquals(
            RemoveDirectoryEntryResult.Removed,
            removed
        )

        assertEquals(
            ResolveJoinCodeResult.NotFound,
            directory.resolve(entry.joinCode)
        )
    }

    private fun liveEntry(
        sessionId: String = "session-1",
        status: SessionStatus = SessionStatus.LIVE,
        expiresAt: Long = 100_000L
    ): SessionDirectoryEntry {
        return SessionDirectoryEntry(
            joinCode = "1234-5678",
            sessionId = sessionId,
            sessionName = "Friday Group",
            hostUserId = "host-1",
            displayId = "pi-1",
            status = status,
            displayMode = DisplayMode.AUTO_LATEST,
            createdAt = 100L,
            actualStartedAt = 200L,
            expiresAt = expiresAt
        )
    }
}

private class FakePubNubMetadataTransport :
    PubNubMetadataTransport {

    private val records =
        linkedMapOf<String, PubNubMetadataRecord>()

    private var nextEtag = 1

    override suspend fun get(
        metadataId: String
    ): PubNubMetadataRecord? {
        return records[metadataId]
    }

    override suspend fun set(
        metadataId: String,
        name: String,
        custom: Map<String, Any?>,
        type: String,
        status: String,
        ifMatchesEtag: String?
    ): PubNubMetadataRecord {
        val existing =
            records[metadataId]

        if (
            ifMatchesEtag != null &&
            existing?.eTag != ifMatchesEtag
        ) {
            throw IllegalStateException(
                "Metadata eTag does not match."
            )
        }

        val record =
            PubNubMetadataRecord(
                id = metadataId,
                custom = custom.toMap(),
                eTag = "etag-${nextEtag++}"
            )

        records[metadataId] = record

        return record
    }

    override suspend fun remove(
        metadataId: String
    ) {
        records.remove(metadataId)
    }

    override suspend fun close() = Unit

    fun seed(
        metadataId: String,
        entry: SessionDirectoryEntry
    ) {
        records[metadataId] =
            PubNubMetadataRecord(
                id = metadataId,
                custom = mapOf(
                    "protocolVersion" to
                            entry.protocolVersion.toString(),

                    "joinCode" to
                            entry.joinCode,

                    "sessionId" to
                            entry.sessionId,

                    "sessionName" to
                            entry.sessionName,

                    "hostUserId" to
                            entry.hostUserId,

                    "displayId" to
                            entry.displayId,

                    "sessionStatus" to
                            entry.status.name,

                    "displayMode" to
                            entry.displayMode.name,

                    "createdAt" to
                            entry.createdAt.toString(),

                    "actualStartedAt" to
                            entry.actualStartedAt.toString(),

                    "expiresAt" to
                            entry.expiresAt.toString()
                ),
                eTag = "seed-etag"
            )
    }
}