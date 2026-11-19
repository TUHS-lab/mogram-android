<p align="center">
  <img src="TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Mogram" width="96">
</p>

<h1 align="center">Mogram for Android</h1>

<p align="center">
  <a href="README.ru.md">Русский</a> · <b>English</b>
</p>

Android client based on the official [Telegram for Android](https://github.com/DrKLO/Telegram) source code, the sibling of [Mogram for desktop](https://github.com/TUHS-lab/mogram).

> **Status: early.** This repository currently contains the rebranded base (name, icon, package, build in CI). The Mogram features from the desktop app are brought over step by step, see the roadmap below. Nothing here is an official Telegram product.

## Roadmap

- [x] Rebranded base: name, icon, package `app.mogram.android`, no official API keys in the code
- [ ] First successful build in GitHub Actions (the workflow is written, the first run failed on submodules, fixed, not re-run yet)
- [x] Gray-only palette (all blue shades turn gray), graphite dark themes, rounded dialogs
- [x] Privacy: ghost mode and streamer mode (hides your own phone number). Hides your username too; ID: not yet
- [x] Keep messages the other person deleted, with a "deleted" mark in the message time (edit history: not yet)
- [x] Security: decoy passcode (silently signs out of all accounts). Lock right away when you leave the app. Round video blur (Android 12+)
- [x] Music: SoundCloud search, player and lyrics
- [x] Chat commands (.id .pin .unpin .del .mute .unmute) and text shortcuts like /hi. Tools: message card image (selected messages that are on screen)
- [ ] Plugins

> The checked items are written but **not built or tested yet**: the first successful build is still ahead.

## Build it yourself

You need **your own** Telegram API credentials: <https://core.telegram.org/api/obtaining_api_id>. Official keys are not used here, by Telegram's rules for third-party apps.

### In GitHub Actions (no tools on your machine)

1. Fork this repository.
2. In **Settings → Secrets and variables → Actions** add two secrets: `MOGRAM_API_ID` and `MOGRAM_API_HASH`.
3. Optional but recommended: `MOGRAM_KEYSTORE_BASE64` (your signing keystore in base64), `MOGRAM_KEYSTORE_PASSWORD`, `MOGRAM_KEY_ALIAS`, `MOGRAM_KEY_PASSWORD`. Without them a temporary key is generated and the next build cannot be installed over the previous one.
4. **Actions → Android → Run workflow.** The APK appears in the run artifacts (and in a release if you give a tag).

### Locally

Android Studio, JDK 17, Android SDK 36, NDK 27.2.12479018. Put your `api_id` and `api_hash` into `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`, then:

```bash
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
```

## Notes

- **Push notifications** need your own Firebase project: replace both `google-services.json` files (in `TMessagesProj_App` and `TMessagesProj_AppStandalone`) with yours. Without that the app works, but notifications arrive only while it is running.
- Please do not use the name Telegram or its standard logo for your own builds, and publish your code, as the original README asks.

## License

GPL-2.0, same as the original Telegram for Android. Based on [DrKLO/Telegram](https://github.com/DrKLO/Telegram), © Telegram FZ-LLC and contributors. Changes © 2026 [@tuhss](https://t.me/tuhss).
