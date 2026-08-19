# Group AAC

Group AAC is a native Android prototype for supporting group conversations involving people with aphasia, facilitators, speech-language pathologists, family members, volunteers, and other conversation partners.

The system is designed to reduce coordination burden during group conversation by giving participants accessible ways to compose messages, signal conversational needs, preserve content, and interact with a facilitator. A separate shared display can surface selected group content without requiring the display itself to run the Android app.

## Current prototype status

Updated: July 30, 2026

The current prototype has moved from a custom backend architecture to a peer-based Android + PubNub + Raspberry Pi design:

```text
Participant Android apps ─┐
Facilitator Android app  ─┼── PubNub ── Raspberry Pi display client
Room on each Android app ─┘
```

Current manual integration status:

- A facilitator can create a local session and launch it by scanning a Raspberry Pi pairing QR.
- The Python Pi simulator can bind to the session, persist its binding, and subscribe to session display commands.
- PubNub App Context provides the eight-digit session join-code directory.
- Two Android emulators can enter the same live session as facilitator and participant.
- The Python simulator receives Android display commands and persists the resulting display state.
- Android and Python unit test suites pass on the current development branch.

This remains a controlled research prototype rather than a production communication service.

## Implemented functionality

### Accounts and navigation

- Local account creation and account switching.
- Participant-oriented and facilitator-oriented home experiences.
- Persistent active account and active session restoration with DataStore.
- Adaptive Compose layouts for phone and tablet-sized screens.

### Session lifecycle

- Immediate and scheduled local session creation.
- Draft and scheduled sessions remain non-joinable until display binding succeeds.
- Display pairing through Google Code Scanner.
- Raspberry Pi bind, bound, bind-failed, unbind, and unbound protocol events.
- Eight-digit short-code registration and lookup through PubNub App Context.
- Participant joining by session code.
- Facilitator access requests with host approval or rejection.
- Host-only session ending with join-code cleanup and Pi unbinding.

### Participant experience

- Compose and send group or facilitator-directed messages.
- Save drafts and messages.
- Add locally stored image, video, or file attachments.
- Send conversational signals such as help, readiness, waiting, agreement, or disagreement.
- Replace or clear the participant's own signal.
- View session and account settings.

### Facilitator experience

- Participant overview cards with activity and signal information.
- Approve or reject facilitator join requests.
- Snooze or unsnooze participant signals without clearing participant-owned signals.
- Review group messages and session history.
- Show, restore, pin, unpin, clear, or delete displayable messages.
- Add participant notes and quick logs.
- View session summary information.

### Realtime and reliability

- Account-scoped PubNub clients.
- Session-specific public, facilitator, private-user, display-command, and display-event channels.
- Room-backed transactional outbox for realtime events.
- Atomic outbox claims, retry leases, delayed WorkManager retries, and network constraints.
- Account isolation that prevents queued events from being published through another user's PubNub client after an account switch.
- Realtime history replay and processed-event deduplication.
- Persisted optimistic display state with acknowledgement correlation and stale acknowledgement rejection.

### Raspberry Pi protocol reference

The package under [`pi/`](pi/) is a Python protocol simulator and test harness. It is not the production Raspberry Pi application and is not a backend for Android.

It currently supports:

- idle pairing JSON and QR generation;
- pairing nonce rotation and expiry;
- display binding and unbinding;
- one-active-session enforcement;
- persisted binding recovery after restart;
- duplicate-command idempotency;
- session display commands for show, restore, pin, unpin, clear, and display-mode changes;
- persisted headless display state;
- correlated display acknowledgements.

The professor's production Raspberry Pi client is being implemented separately in C++ and should follow the documented protocol contract.

## Architecture

```text
Jetpack Compose screen
        ↓
ViewModel
        ↓
Repository
        ↓
Room / DataStore / local file storage / PubNub / Pi protocol boundary
```

Room is the local source of truth for users, settings, sessions, memberships, messages, signals, notes, display state, and realtime reliability state. PubNub is used for cross-device event transport and short-code discovery, not as the local application database.

The shared monitor is intentionally not implemented as an Android screen. Android publishes display commands to the Raspberry Pi client, which owns the physical display experience.

## Repository structure

```text
app/                    Android application
  src/main/java/...     Compose UI, ViewModels, repositories, Room, PubNub
  src/test/...          JVM, Robolectric, protocol, and repository tests
  schemas/              Room schema snapshots

pi/                     Headless Python Pi simulator and protocol harness

docs/                   Architecture, protocol, reliability, and testing docs
```

Important protocol documents:

- [`docs/pi-display-protocol-contract.md`](docs/pi-display-protocol-contract.md)
- [`docs/pi-display-protocol-fixtures.json`](docs/pi-display-protocol-fixtures.json)
- [`docs/pi-cpp-integration-checklist.md`](docs/pi-cpp-integration-checklist.md)
- [`docs/end-to-end-test-procedure.md`](docs/end-to-end-test-procedure.md)
- [`docs/outbox-and-recovery.md`](docs/outbox-and-recovery.md)

## Android setup

### Requirements

- Android Studio
- JDK 17
- Android SDK 35
- A Google Play-enabled emulator for Google Code Scanner
- A PubNub keyset with Publish/Subscribe and App Context enabled

Create `pubnub.properties` in the repository root:

```properties
PUBNUB_PUBLISH_KEY=pub-c-...
PUBNUB_SUBSCRIBE_KEY=sub-c-...
```

Do not add a PubNub secret key to the Android application.

### Current PubNub keyset requirement

The current display acknowledgement channel is:

```text
session.<sessionId>.display.events
```

Because this channel has four dot-separated levels, the current keyset must have **Wildcard Subscribe disabled**. Otherwise PubNub rejects acknowledgements with `Wildcard maximum depth exceeded`.

A future protocol refinement should flatten this channel name so the prototype does not depend on that keyset setting.

### Build and test

From the repository root:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
```

Install the debug APK with Android Studio or:

```bash
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Python Pi simulator setup

From the repository root:

```bash
cd pi
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
```

Set the same PubNub publish and subscribe keys used by Android:

```properties
PUBNUB_PUBLISH_KEY=pub-c-...
PUBNUB_SUBSCRIBE_KEY=sub-c-...
GROUP_AAC_DISPLAY_ID=group-room-pi-01
GROUP_AAC_DISPLAY_NAME=Conversation Room Display
GROUP_AAC_PI_STATE_PATH=runtime/pi_state.json
GROUP_AAC_PI_ARTIFACTS_DIR=runtime
GROUP_AAC_PAIRING_TTL_SECONDS=300
```

Run the Python tests:

```bash
python -m pytest tests -q
```

Start a fresh simulator:

```bash
rm -rf runtime
python -m group_aac_pi.main
```

The simulator creates:

```text
pi/runtime/pairing.json
pi/runtime/pairing_qr.png
pi/runtime/pi_state.json
```

Scan `pairing_qr.png` from the Android facilitator flow. After binding, inspect the headless simulated display state with:

```bash
python -m json.tool runtime/pi_state.json
```

The simulator does not open a graphical display window. Display behavior is verified through terminal logs, `pi_state.json`, and acknowledgements received by Android.

## Basic integration flow

1. Start the Python simulator and leave its terminal running.
2. Launch the app on a facilitator emulator and a participant emulator.
3. Create a facilitator account and a session.
4. Scan `pi/runtime/pairing_qr.png` to launch the session.
5. Record the generated eight-digit join code.
6. Enter that code from the participant emulator and join.
7. Send a participant group message.
8. From the facilitator, show, pin, unpin, clear, and restore messages.
9. Verify Python terminal acknowledgements and `runtime/pi_state.json` transitions.
10. End the session and confirm the Pi returns to `PAIRING_AVAILABLE` with a new pairing QR.
