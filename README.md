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
  OpenRouter, and the models to try are a list you edit.
- It can post straight to Bluesky and to an Instagram professional account,
  once you add your own account details in settings: the rendered image, your
  text and the alt text. Replies and conversations stay in those services' own
  apps. With none of AI, Bluesky or Instagram set up, the app never connects
  to anything. See [Posting directly](#posting-directly).
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

## Posting directly

Sharing through the system share sheet needs no setup. Posting straight from
the app does, once per service. Every value below is typed into the app's
Settings on the phone; none of it belongs in this repository.

### Bluesky

1. In Bluesky: Settings, Privacy and security, App passwords, add one.
2. In ShotQuill Settings, Bluesky: enter your handle and that app password,
   then use "Check sign-in".

Posts are limited to 300 characters, hashtags included. If the caption is
longer, write a shorter text in the post's "Bluesky text" box.

Each post has its own hashtags box per service, because the tags that work on
Bluesky are not the ones that work on Instagram. Settings holds the usual tags
for each, which new posts start with. On Bluesky a full handle in the text or
the tags, such as `@name.bsky.social`, becomes a real mention.

Mentions are checked while you write. For Bluesky the app asks Bluesky's open
directory whether the handle exists (only the handle is sent, with no
sign-in) and shows the account's name, or says that nobody has that handle or
that a bare `@name` needs its full handle. Instagram gives an app that posts
with an Instagram sign-in no way to ask whether an account exists, so there
the app only catches names Instagram would not accept and offers each one as
a link to open the profile and see for yourself.

### Instagram

Instagram's API only publishes to professional (Business or Creator) accounts,
and it does not accept an upload: Meta fetches the image from a public web
address. ShotQuill therefore puts the image in your own Firebase Storage for a
moment and removes it once the post is made. Menu names in the two consoles
change from time to time, so treat these as a guide.

Image hosting, in the Firebase console for a project on the Blaze plan:

1. Build, Storage: get started if Storage is not yet enabled.
2. Build, Authentication, Sign-in method: enable Email/Password. Under Users,
   add a user for uploads and copy its User UID.
3. Storage, Rules: inside `match /b/{bucket}/o { ... }` add

   ```
   match /shotquill-temp/{file} {
     allow read, write: if request.auth != null && request.auth.uid == "THE_USER_UID";
   }
   ```

4. Project settings, Your apps: add an Android app with the package name
   `com.digitumdei.shotquill` and download `google-services.json`. Do not add
   that file to the repository. An existing web app's `firebaseConfig` block
   works too.
5. In ShotQuill Settings, Image hosting for Instagram: paste the contents of
   that file (or the `firebaseConfig` block), then enter the upload user's
   email and password.

Instagram access, at developers.facebook.com:

1. Create an app and add the Instagram product, choosing the API setup with
   Instagram login.
2. Add your Instagram professional account to the app as an Instagram tester,
   and accept the invitation from within Instagram.
3. Generate an access token for that account with the
   `instagram_business_content_publish` permission.
4. In ShotQuill Settings, Instagram: save the token, then use "Check
   connection". It confirms the account and that image hosting accepts an
   upload, without posting anything.

The token lasts about sixty days and the app renews it whenever you post.
Instagram's API takes feed shapes from 4:5 to 1.91:1, so a 9:16 crop has to go
through Share instead.

## Local settings

The look, the logo placement, the usual crop shape and the caption snippets are
stored on the phone behind the shared `LocalSettingsRepository` abstraction.
Each post's framing is kept by `PhotoTreatmentStore`.

The AI service address and the model list per AI operation live with the other
settings. The API key is kept separately in `EncryptedSharedPreferences`,
backed by an Android Keystore `MasterKey`; settings only ever shows a masked
hint of it and lets you replace or remove it. The Bluesky app password, the
Instagram access token and the Firebase upload password are stored the same
way. No account details live in this repository: everything is entered on the
phone, including which Firebase project to use.
