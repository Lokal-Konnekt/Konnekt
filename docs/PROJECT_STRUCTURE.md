# Konnekt — project structure

This guide records the responsibilities found in the source tree. Generated
build output, local credentials and the Git database are not application source.

## Android: `app/`

- `src/main/java/com/music/konnekt/`: application startup, activity and platform code.
- `auth/`: Google/YouTube sessions, encrypted preferences, Konnekt account and cloud encryption.
- `data/`: settings, music integrations, local playlists, lyric/file support, Canvas, statistics, sources, network storage and update delivery.
- `playback/`: Media3 service, queue, cache, stream resolution, crossfade/Automix, output processing, DSD, Cast and shared listening integration.
- `download/`: download jobs, records, file storage and lyric tagging.
- `ui/`: Android screens, dialogs, settings, Replay, account and player integration.
- `widget/`: widget state, provider classes, controls and layout updates.
- `src/main/res/`: Android layouts, launcher/notification artwork, themes, 16 string locales and backup/sharing rules.
- `src/main/cpp/`: Android CMake targets, JNI bridges, DSD decimation and DST decoding.
- `src/debug/`: design previews; `src/test/`: JVM regression tests.
- `incomplete-konnekt-source-backup/` and `obsolete-widget-source-backup/`: local reference copies outside the configured `src/` source sets. They are not compiled into the app.

The source namespace and Android application ID are `com.music.konnekt`.
Existing Android preference filenames and downloaded-file references remain the
same. App component names now follow the unified namespace; a pinned home-screen
widget may need to be added again if its launcher retains the previous component.

## Shared domain: `shared/`

- `commonMain/`: models and platform-independent contracts, playback analysis types and party protocol.
- `jvmSharedMain/`: music repository, Innertube, lyric providers/parsers, source matching, HTTP, scrobbling, synchronization and audio analysis used by Android and desktop.
- `androidMain/` and `jvmMain/`: platform implementations.
- `commonTest/` and `jvmTest/`: model, protocol and network tests.

## Shared interface: `sharedUi/`

- `jvmSharedMain/`: Compose home/library/search screens, playback UI, synchronized lyrics, Replay card, themes and icons.
- `commonMain/composeResources/`: interface translations and shared artwork.
- `androidMain/` and `jvmMain/`: window, platform graphics and interaction hooks.
- `jvmTest/`: lyric focus, timing, theme and UI logic checks.

## Desktop: `desktopApp/`

- `src/main/kotlin/com/music/konnekt/desktop/`: desktop application, music integration, playback, accounts, local files, downloads, statistics, persistence and platform services.
- `src/main/composeResources/`: desktop artwork; `src/main/resources/`: desktop translations.
- `src/test/`: desktop regression tests.
- `native/`: analysis JNI, Windows window/audio integration and media controls.
- `packaging/`: Windows/Linux launchers, icons, AppImage assembly and packaging rules.

Desktop JVM properties and build parameters use the `konnekt.*` prefix. A schema
check can import one compatible earlier Java preference store, including its
statistics, while retaining current values. Ambiguous stores are not merged.
Recorded download paths remain usable; this migration does not move audio files.

## Native processing: `native/`

- `analyzer/`: shared audio analysis, resampling, spectral processing and feature extraction.
- `ffmpeg/`: FFmpeg build support.

Android native targets use the `konnekt_` prefix. JNI names and Kotlin loaders
must be changed together. Original author and third-party license notices remain
with their implementations.

## Listen Together: `backend/`

- `main.go`: HTTP routes, application wiring and invite page.
- `clock/`, `codes/`, `config/`: server time, party codes and configuration.
- `hub/`, `party/`, `protocol/`: WebSocket sessions, party lifecycle and messages.
- `presence/`: application presence service.
- `deploy/`: self-hosting templates; `render.yaml`: Render blueprint.
- `tools/`: backend probing utilities.

The Go module and internal imports point to the current repository. Deployment
templates require the user's own host, domain and credentials; renaming the
source does not deploy or reconfigure an existing remote server.

## Cloud: `supabase/`

`migrations/` contains the encrypted account snapshot schema and access policies.
The Android client integrates the configured cloud account and backup service.
Audio files are excluded from that cloud backup.

## Documentation, release tools and configuration

- `README.md`: product presentation, install/build instructions and navigation.
- `CHANGELOG.md`: release history; `DESKTOP.md`: desktop development/packaging.
- `docs/`: lyrics translation, desktop releases, Android update publishing and this guide.
- `tools/verify-release.ps1`: Android package, signature, version and alignment checks.
- `tools/branding-audit.mjs`: text/path audit for a supplied brand expression.
- `tools/replace-brand.mjs`: generic replacement utility with a dry run by default.
- `.github/workflows/`: Android/desktop builds, checks and explicit deployment workflows.
- `gradle/`, `gradlew`, `gradlew.bat`: Gradle wrapper; module build scripts configure targets and dependencies.
- `codebase-map/`: generated codebase visualization.
- `.agents/`, `skills-lock.json`: local agent tooling, separate from runtime functionality.
- `Logo.png`: supplied K artwork. Desktop and Android use the current K assets.

Local files such as `local.properties`, `keystore.properties`, APKs, keystores,
logs and generated build directories are ignored by Git. Signing keys are not
renamed or regenerated as part of source branding. Author attribution and
component-specific license notices are retained.

## Validation of the identity update

The production Android build and all Kotlin test suites passed: 983 Android,
46 shared, 35 shared UI and 337 desktop tests. Backend `go vet ./...` and
`go test ./...` also passed.

The arm64 and universal APKs report `com.music.konnekt`, version `1.4.0`
(`14000`). Their signatures match the previous release, their version code
increases, and both pass the 16 KB ZIP alignment check. No Android device was
connected for installation or runtime validation. Desktop native packaging was
not validated.
