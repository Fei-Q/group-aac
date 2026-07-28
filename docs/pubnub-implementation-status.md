# PubNub Implementation Status

## Stage 1 - Baseline and audit

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-realtime-foundation`
Starting commit: `5be1ff5 Add PubNub SDK and configuration properties`

### Baseline repository state

- Working tree was clean before stage work.
- Root `.gitignore` did not yet ignore `pubnub.properties`.
- Root `build/` output and IDE-local directories were already ignored.
- A root `local.properties` file exists and remains unmodified.

### Baseline build and test status

- `./gradlew :app:assembleDebug`
  - Failed in `:app:kspDebugKotlin`.
  - PubNub and transitive Kotlin artifacts were compiled with Kotlin metadata `2.2.0` while the project expected `2.0.0`.
  - A KSP incremental failure also appeared: missing generated file `UserDao_Impl.java`.
- `./gradlew :app:testDebugUnitTest`
  - Failed in `:app:kspDebugKotlin`.
  - Same Kotlin metadata incompatibility blocked unit tests before execution.
- Connected tests were not part of this baseline stage.

### Architecture map before implementation

#### Identity and account creation

- [`UserEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserEntity.kt) used generated `id` as the primary key, retained deprecated account-level `role`, and tracked `lastLoginAt`.
- [`AccountRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/AccountRepository.kt) created accounts with `IdUtils.newId()` rather than user-created immutable UIDs.
- [`AccountViewModel`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/account/AccountViewModel.kt) depended on `AccountRepository`, which in turn depended directly on `UserDao` rather than a registration abstraction.
- [`UserSettingsEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserSettingsEntity.kt) still contained deprecated account-level role/default-role compatibility fields and monitor defaults that need to move cleanly into the new schema.

#### Room schema and persistence

- [`AppDatabase`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/AppDatabase.kt) was on Room schema version `5` with old migrations still present.
- `exportSchema` was `false`, while an old exported schema file still existed at [`app/schemas/com.example.groupaac.data.AppDatabase/1.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/schemas/com.example.groupaac.data.AppDatabase/1.json).
- Active session persistence was per-user through [`ActiveSessionStore`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/session/ActiveSessionStore.kt), keyed by generated user ID.
- One app-install database file (`group_aac.db`) was already used, which aligns with the plan.

#### Session and repository layer

- [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt) owned local session lifecycle, join approval logic, membership, and active-session activation.
- Session membership already used per-session roles (`PARTICIPANT`, `FACILITATOR`, `HOST`), which aligns with the plan.
- `SessionEntity` lacked session-level realtime/display settings such as `DisplayMode`, timestamps for updates, and richer lifecycle metadata.
- Join requests already had a dedicated entity/DAO path, but the data model still needs cleanup around immutable UID usage and future realtime decisions.

#### Realtime and Raspberry Pi integration

- [`PiClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/pi/PiClient.kt) was a narrow transport abstraction focused on direct Pi operations rather than a general session realtime transport.
- [`AppContainer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/AppContainer.kt) wired a single app-wide `MockPiClient`, not one client per active account.
- Repositories (`SessionRepository`, `MessageRepository`, `SignalRepository`) called the transport directly instead of persisting outbox work and routing through a realtime manager.

#### Signals and Resolve behavior

- [`StatusSignalEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/StatusSignalEntity.kt) still modeled `state` plus `resolvedAt`, which does not match the new create/snooze/clear-only design.
- [`SignalRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SignalRepository.kt) still exposed:
  - `resolveSignal`
  - `resolveSignalsForUser`
  - global `snoozeSignal` / `unsnoozeSignal`
- Current snooze semantics were not facilitator-specific, so one facilitator could hide a signal for others.

#### Display controls

- Display commands existed through the Pi transport, but there was no explicit realtime protocol envelope, outbox, cursor, or acknowledgement persistence.
- The current model does not yet separate:
  - session display command channel
  - session display event channel
  - persistent display state in Room
- The planned explicit `display.show_message`, `display.clear`, `display.pin_message`, and acknowledgement handling were not yet wired end to end.

#### Tests before implementation

- Repository tests existed for local session logic in [`SessionRepositoryTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/repository/SessionRepositoryTest.kt).
- UI tests existed for account/home/settings/session screens.
- There were no serializer/parser/routing tests for a realtime protocol yet.
- There were no reliability tests for outbox, deduplication, cursor recovery, or display command ordering yet.

### Stage 1 notes for upcoming work

- First repair target is the Kotlin/PubNub compatibility issue blocking all builds.
- Stage 2 will require a schema reset, migration removal, identity rewrite, and foreign-key cleanup across entities, DAOs, repositories, and tests.
- Stage 4 onward will replace direct Pi transport calls with a broader realtime client boundary and fake implementation.

## Stage 2 - Fresh identity and Room schema

Status: complete

### Implemented

- Reset Room to schema version `1` in [`AppDatabase`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/AppDatabase.kt).
- Removed legacy migration chain and enabled destructive recreation for the development schema reset.
- Reworked `users` to store the immutable account primary key in the `uid` database column while preserving a stable Kotlin `id` property for the rest of the codebase transition.
- Removed deprecated account-role and last-login persistence from [`UserEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserEntity.kt).
- Removed deprecated `defaultRole` compatibility state from [`UserSettingsEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserSettingsEntity.kt).
- Added session-level [`DisplayMode`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/DisplayMode.kt) to [`SessionEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/SessionEntity.kt) with default `AUTO_LATEST`.
- Updated Room queries and joins to use the new `uid` column in users.
- Regenerated the exported schema at [`app/schemas/com.example.groupaac.data.AppDatabase/1.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/schemas/com.example.groupaac.data.AppDatabase/1.json).

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- The Kotlin property name transition is intentionally incremental: the database primary key is now `uid`, while much of the existing app code still accesses the same value through `UserEntity.id`.
- Date-sensitive home-screen tests were updated to use dates in the future relative to Tuesday, July 28, 2026.

## Stage 3 - Registration abstraction

Status: complete

### Implemented

- Added [`UserIdRegistry`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/account/UserIdRegistry.kt) with:
  - `CreateAccountRequest`
  - `CreateAccountResult`
  - `LocalUserIdRegistry`
- Enforced UID validation with the required regex: `^[a-z0-9][a-z0-9_]{2,23}$`.
- Account creation now inserts `UserEntity` and `UserSettingsEntity` transactionally via `withTransaction`.
- Duplicate primary-key insertion is converted to `AlreadyTaken`.
- [`AccountRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/AccountRepository.kt) now depends on `UserIdRegistry` instead of creating IDs directly.
- Updated account UI/view-model flow to collect a user-created UID plus display name and surface validation/duplicate-account errors.

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- This is still a local-only registry implementation by design.
- The interface boundary is now ready for a future remote UID registry and token/session authority work.

## Stage 4 - PubNub foundation

Status: complete

### Implemented

- Root `pubnub.properties` remains the only supported source of PubNub keys, and the root `.gitignore` now ignores it.
- Removed the old tracked `app/pubnub.properties` file.
- Added generated `BuildConfig` fields for:
  - `PUBNUB_PUBLISH_KEY`
  - `PUBNUB_SUBSCRIBE_KEY`
- Added [`PubNubRuntimeConfig`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/PubNubRuntimeConfig.kt) and `PubNubConfigProvider` to read runtime PubNub config from `BuildConfig`.
- Added a new broader realtime transport boundary:
  - [`SessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionRealtimeClient.kt)
  - [`FakeSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/FakeSessionRealtimeClient.kt)
- Added account-scoped realtime lifecycle management in [`RealtimeClientManager`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/RealtimeClientManager.kt).
- Added future auth/session boundaries:
  - `PubNubTokenProvider`
  - `SessionAuthority`
- Wired account creation, account switching, and sign-out to activate/deactivate the active realtime client.
- Added [`DelegatingPiClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/DelegatingPiClient.kt) so existing repositories preserve their local behavior while the new client lifecycle foundation sits underneath.
- Added stage-4 lifecycle tests in:
  - [`RealtimeClientManagerTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RealtimeClientManagerTest.kt)
  - [`RecordingRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RecordingRealtimeClient.kt)

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
  - One transient KSP cache failure occurred first under `app/build/kspCaches/debug/backups`; rerunning after regeneration succeeded.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- Repositories still speak through the legacy `PiClient` method surface, but that client is now explicitly marked deprecated and is backed by the new lifecycle-managed realtime boundary.
- The transport implementation is still fake/local at this stage by design; actual PubNub event publishing/subscription begins in the next stages.

## Stage 5 - Realtime protocol

Status: complete

### Implemented

- Added channel helper functions in [`RealtimeChannels`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeChannels.kt) for:
  - `session.<sessionId>.public`
  - `session.<sessionId>.facilitator`
  - `session.<sessionId>.<userId>`
  - `session.<sessionId>.display`
  - `session.<sessionId>.display.events`
  - `display.<displayId>.control`
- Added canonical protocol models in [`RealtimeEvent`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEvent.kt):
  - `RealtimeEvent`
  - `ReceivedRealtimeEvent`
- Added allowed event-type constants in [`RealtimeEventTypes`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventTypes.kt).
- Added manual serializer/parser support in [`RealtimeEventCodec`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventCodec.kt) using `JsonObject` payloads and omission of nullable fields when absent.
- Added channel routing in [`RealtimeEventRouter`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventRouter.kt).
- Added protocol tests in [`RealtimeProtocolTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/protocol/RealtimeProtocolTest.kt) covering:
  - channel helper output
  - serializer/parser round-trip
  - omission of null transport fields
  - preservation of `inReplyToEventId` and `expiresAt`
  - supported/unsupported channel routing

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- The protocol layer intentionally does not introduce `schemaVersion`, app-generated sequence numbers, `message.edited`, `message.received`, `aac.signal.updated`, `aac.signal.resolved`, or `display.take_down`.
- Timetoken ordering metadata is represented through `ReceivedRealtimeEvent`; actual cursor/deduplication logic follows in stage 6.

## Stage 6 - Reliability

Status: complete

### Implemented

- Added persistence for realtime reliability state in Room schema version `2`:
  - [`OutboxEventEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/OutboxEventEntity.kt)
  - [`ProcessedEventEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/ProcessedEventEntity.kt)
  - [`ChannelCursorEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/ChannelCursorEntity.kt)
  - [`DisplayStateEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/DisplayStateEntity.kt)
- Added [`OutboxEventState`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/OutboxEventState.kt) and Room converters for outbox lifecycle storage.
- Added [`ReliabilityDao`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/dao/ReliabilityDao.kt) with transactional processed-event and cursor recording.
- Added [`RealtimeReliabilityStore`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStore.kt) to provide:
  - local-first outbox persistence
  - bounded retry/backoff scheduling
  - processed-event deduplication by `eventId`
  - channel cursor updates from PubNub timetokens
  - display-command ordering and stale-command rejection
- Added recovery interfaces for future persisted history/snapshot replay in [`RealtimeRecoverySources`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeRecoverySources.kt).
- Added reliability unit coverage in [`RealtimeReliabilityStoreTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStoreTest.kt) for:
  - retry backoff caps
  - processed-event deduplication
  - stale display-command rejection
  - expired outbox exclusion

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Failed first because the new Room-backed reliability test was missing the Robolectric runner during `ApplicationProvider` setup.
  - Passed on Tuesday, July 28, 2026, after aligning the new test with the repo's existing Robolectric unit-test pattern.

### Notes

- The reliability layer is now persisted locally, but repositories are not yet publishing through it; stage 7 wires the session flows onto these primitives.
- Display-command freshness is currently enforced through PubNub timetoken ordering, which matches the implementation plan's received-order metadata requirement.

## Stage 7 - Session synchronization

Status: complete

### Implemented

- Extended [`SessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionRealtimeClient.kt) with generic canonical-event publishing and per-channel observation while preserving the existing Pi-oriented calls used by the current app flow.
- Upgraded [`FakeSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/FakeSessionRealtimeClient.kt) into an in-memory realtime bus that records published canonical events with synthetic timetokens for unit verification.
- Added a repository-facing synchronization boundary in [`SessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/SessionRealtimeSync.kt), plus a safe no-op default to preserve local behavior when realtime is unavailable.
- Added canonical session/message payload DTOs and snapshot payload support in [`RealtimePayloads`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/RealtimePayloads.kt).
- Added [`DefaultSessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt) to:
  - publish session lifecycle, membership, facilitator request/decision, message, and targeted snapshot events to the required channels
  - enqueue and mark outbox events through the stage-6 reliability store
  - apply incoming canonical events back into Room with processed-event deduplication and channel-cursor updates
- Wired repositories to publish canonical events after their local-first writes:
  - [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt)
  - [`MessageRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/MessageRepository.kt)
- Added synchronization unit coverage in [`DefaultSessionRealtimeSyncTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSyncTest.kt).

### Verification

- `./gradlew :app:assembleDebug`
  - Failed first because the new sync layer used helper names that did not match the existing `RealtimeChannels` and `RealtimeEventCodec` APIs.
  - Failed second because the Kotlin serialization plugin had not yet been declared in the root Gradle plugin block for this project.
  - Passed on Tuesday, July 28, 2026, after aligning the sync layer with the repo’s actual protocol helpers and registering the serialization plugin.
- `./gradlew :app:testDebugUnitTest`
  - Failed first because [`RecordingRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RecordingRealtimeClient.kt) still implemented the older realtime-client surface.
  - Failed second because the initial outbox assertion checked the retry queue rather than the persisted sent event.
  - Passed on Tuesday, July 28, 2026, after updating the test double and asserting against the stored sent outbox row.

### Notes

- Local Room behavior remains the UI source of truth and still works when realtime synchronization is a no-op.
- Live subscription collectors and reconnect replay orchestration are still intentionally thin at this stage; the new sync service provides the publish/apply foundation that stages 8 and 9 now build on.
