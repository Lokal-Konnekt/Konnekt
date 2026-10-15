# Publishing updates for Konnekt

Konnekt checks the latest published stable release at
`https://api.github.com/repos/Lokal-Konnekt/Konnekt/releases/latest`.

For every release:

1. Increase `versionCode` in `app/build.gradle.kts`, and set the new `versionName`.
2. Build `:app:assembleProdRelease` with the existing release keystore.
3. Run `tools/verify-release.ps1` against the previous release to verify package, signature, version code and ZIP alignment.
4. Publish a stable GitHub release with a tag matching `versionName`, for example `v1.4.1`.
5. Attach the signed APK files as release assets, for example `Konnekt-1.4.1-arm64.apk` and `Konnekt-1.4.1.apk` (universal). Source archives and ZIP artifacts are not APK assets.
6. Add the release notes to the GitHub release body. The dialog displays these notes as written; interface labels are localized independently.

Drafts, prereleases and releases with no compatible uploaded APK are not offered.
The app chooses a supported CPU build before the universal fallback. Downloads
are validated before installation, including GitHub's SHA-256 digest when supplied.
The package must remain `com.music.konnekt`, the signing certificate must remain
compatible, and Android's version code must increase.

The dialog checks on foreground visits and limits checks to once per minute.
It announces each detected version once per activity session and again after a
fresh launch. Closing the dialog leaves an active download running in the app's
process. A completed APK survives process restarts unless Android clears its
cache; partial downloads restart. Android confirms the installation and may ask
for permission to install apps from Konnekt.

## Dialog design

- Existing Konnekt logo and Material theme color roles in light and dark themes.
- Maximum width 420 dp, 24 dp padding, 28 dp corner radius; scrolling for smaller screens and larger fonts.
- A 240 ms opacity/scale entrance; reduced animation shows the dialog immediately.
- Primary download/install action, secondary dismissal/cancellation, and distinct downloading, verifying, ready and error states.
- Native dialog focus/back handling, heading semantics, progress semantics and controls of at least 48 dp.
- New text is present in all 16 Android string locales. GitHub release notes are publisher content.

## Verification limits

The release is compiled and its package/signature/alignment are checked locally.
Unit tests cover release selection, recommendation diversity, lyric focus and
network sharing/cancellation. No connected Android device is available in this
workspace, so installation, visual rendering and frame timing require a device check.
