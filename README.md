# ShotQuill

Prepare a photo for Instagram without doing the work in Instagram.

ShotQuill takes a photo you shot or picked, crops it to an Instagram shape,
applies your one consistent look (a fixed set of light and colour adjustments),
stamps your logo (SVG or PNG) in a corner, and hands the finished image to the share sheet
with your caption on the clipboard. The caption can be typed or dictated.

What it deliberately does not do:

- It does not generate the image or the caption. The pixels are your photo,
  adjusted with ordinary crop and colour arithmetic on the phone; the caption
  is the words you type or dictate.
- It uses the network for one optional thing: alt text. If you save an API key
  in settings, a post's alt text can be written by an AI model from the
  cropped photo and your caption, sent only when you ask. Any service that
  speaks the OpenAI chat-completions API works; the address defaults to
  OpenRouter, and the models to try are a list you edit. Without a key the app
  never connects to anything.
  Dictation is done by the phone's own speech recogniser: on the device where
  the phone supports that, otherwise through the phone's default speech
  service, which may process audio on its provider's servers.
- It does not post, schedule or reply for you. Sharing ends at the system
  share sheet.

## Project layout

ShotQuill is a Kotlin Multiplatform project targeting Android for the MVP. UI
is built with Compose Multiplatform so the codebase can grow to additional
targets later without rewriting the view layer.

```
.
├── composeApp/    # Android application + Compose Multiplatform UI
├── shared/        # KMP shared module (domain, business logic)
├── gradle/        # Version catalog + Gradle wrapper
└── .github/       # CI workflow
```

## Local development

The Android toolchain is heavy. Local builds need:

- JDK 17 (Temurin recommended)
- The Android SDK with `compileSdk` 35 (Android Studio installs this for you)

If you have those:

```bash
./gradlew build
./gradlew test
./gradlew koverVerify
```

You do not have to run the full build locally — see "Validating via GitHub
Actions" below for the supported path.

## Validating via GitHub Actions

This repo is designed to be developed from lightweight machines that may not
be able to build Android locally. **GitHub Actions is the validation path**, not
an afterthought.

Every push to `main` and every pull request runs `.github/workflows/ci.yml`,
which executes:

1. `./gradlew build` — compile every module.
2. `./gradlew test` — run all unit tests (shared common tests + Android JVM tests).
3. `./gradlew koverVerify` — fail if line coverage drops below the configured
   threshold (see [Coverage](#coverage)).

If any of those steps fail, the PR is red and must be fixed before merge.

To rely on CI:

1. Push your branch to GitHub.
2. Open a pull request.
3. Watch the **CI** check on the PR. Coverage and test reports are uploaded as
   workflow artifacts when the run completes (or fails).

## Coverage

Coverage is collected with [Kover](https://github.com/Kotlin/kotlinx-kover) and
verified by `./gradlew koverVerify`. The current line-coverage gate is **80%**.
Ratchet it upward with each PR that adds real domain, repository, prompt, or
orchestration code. New code of that kind must ship with tests in the same PR.

Reports are written to:

- `build/reports/kover/html/index.html` (human-readable)
- `build/reports/kover/report.xml` (machine-readable, for coverage badges/tools)

## Local settings

The look, the logo placement, the usual crop shape and the caption snippets are
stored on the phone behind the shared `LocalSettingsRepository` abstraction.
Each post's framing is kept by `PhotoTreatmentStore`.

The AI service address and the model list per AI operation live with the other
settings. The API key is kept separately in `EncryptedSharedPreferences`,
backed by an Android Keystore `MasterKey`; settings only ever shows a masked
hint of it and lets you replace or remove it.
