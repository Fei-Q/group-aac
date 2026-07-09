# Group AAC Android Prototype

First-pass Android Studio project for a native Kotlin / Jetpack Compose prototype of the Group AAC personal-device app.

## Implemented scope

- Local profile login and account creation.
- Responsive login and join-session screens for phone/tablet widths.
- Phone-first participant UI with four tabs: Share, Signal, Social, Settings.
- Tablet-first facilitator UI with four tabs: Participants, Session Log, Summary, Notes.
- Room-backed local entities for users, settings, sessions, members, messages, signals, attachments, notes, and quick logs.
- Preferences DataStore wrapper for active user / last session state.
- Mock Raspberry Pi client boundary for future shared-monitor communication.

## Architecture

The project follows the simplified prototype structure:

```text
Compose screen -> ViewModel -> Repository -> Room / DataStore / File Storage / PiClient
```

The shared monitor itself is intentionally not implemented as an Android screen. The app contains only the Android-side `data/pi/` contract so facilitator actions can later send display commands to the Raspberry Pi software.

## Placeholder resources

These are placeholder vector drawables and should be replaced with final design assets when available:

- `res/drawable/ic_launcher_placeholder.xml`
- `res/drawable/ic_role_participant_placeholder.xml`
- `res/drawable/ic_role_facilitator_placeholder.xml`
- `res/drawable/ic_qr_placeholder.xml`
- `res/drawable/monitor_placeholder.xml`

Replace them by adding final SVG/vector assets in `app/src/main/res/drawable/`, keeping the same filenames or updating their references in Compose files.

## Deferred hooks

- QR scanner: `JoinSessionScreen.kt`, `Scan QR code` button.
- File upload: `ShareScreen.kt`, `Upload file` button and `AttachmentStorage.kt`.
- Raspberry Pi networking: replace `MockPiClient.kt` with an HTTP or WebSocket implementation of `PiClient`.
- Summary export: `SummaryScreen.kt`, `Summary Report` and `Export` buttons.
- Draft browser: `ParticipantSettingsScreen.kt`, `Open drafts` button.

## Screen-size strategy

The login and join-session screens use `BoxWithConstraints` to switch layouts at roughly tablet width (`>= 700.dp`). Participant and facilitator screens are intentionally scoped to phone/tablet respectively in this first pass, but the same strategy can be extended into adaptive phone/tablet/desktop variants later.
