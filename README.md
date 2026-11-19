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

## Screen tests

The screens are tested with Compose's UI test rule running under Robolectric,
so they run with the other unit tests (`./gradlew test`) and need no emulator.
They live in `composeApp/src/androidUnitTest`:

- `PostEditorContentTest` draws the editor from a given state and checks what
  is shown and what each control reports: typing, the mention lines, the
  confirmation before posting, removing a post.
- `PostEditorScreenTest` runs the editor with its view model against stand-in
  storage and services: loading a post, saving once typing pauses, looking up
  a mentioned account.
- `SettingsScreenTest` checks that what is typed in settings is saved.
- `AppNavigationTest` and `MainActivityRestartTest` move between screens and
  check the open screen survives Android restarting the activity.

To run only these:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "*ScreenTest*" --tests "*ContentTest*" --tests "*AppNavigationTest*" --tests "*MainActivityRestartTest*"
```

They do not cover anything that belongs to Android rather than the app (the
camera, the file chooser, the share sheet, speech recognition), real network
calls, or how the screens look. The screen classes stay on the coverage
exclusion list; these tests are their safety net, not part of the 80% figure.

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

While you type a mention the app offers accounts to finish it with: the ones
you have mentioned in earlier posts, and on Bluesky also what Bluesky's own
search suggests for the letters typed so far. Tapping one completes the
mention. For Instagram the app also reads the captions of your recent posts,
where Meta allows it, to collect names you have used before.

Mentions are checked while you write. For Bluesky the app asks Bluesky's open
directory whether the handle exists (only the handle is sent, with no
sign-in) and shows the account's name, or says that nobody has that handle or
that a bare `@name` needs its full handle. Instagram has no dependable way for an app to ask whether an account exists.
The app tries Meta's lookup of professional accounts and shows the account's
name when Meta confirms it; Meta documents that lookup only for another kind
of sign-in, so it may simply never answer, and it knows nothing of personal
accounts. A name it does not confirm is therefore never called wrong: the app
only catches names Instagram would not accept, and offers each one as a link
to open the profile and see for yourself.

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

Instagram access, at developers.facebook.com. The app has to be owned by a
business portfolio:

1. Create an app with the use case "Manage messaging & content on Instagram".
2. Use cases, Customize, then "API setup with Unified login". Make sure the
   permissions include `instagram_basic` and `instagram_content_publish`.
3. Under "Generate access tokens", choose "Add account" to invite your
   Instagram professional account as an Instagram Tester. Accept the invite in
   Instagram on the web: Settings, Website permissions, Apps and websites,
   Tester Invites.
4. "Generate token" and copy it; it is shown once. If you are offered an
   expiry, "never" saves making a new one later.
5. In ShotQuill Settings, Instagram: save the token, then use "Check
   connection". It confirms the account and that image hosting accepts an
   upload, without posting anything.

The app can stay unpublished: that is enough to post to an account that has a
role on it. An older app that still offers "API setup with Instagram login"
works too. Its tokens begin with `IG`, need the
`instagram_business_content_publish` permission, last about sixty days, and
are renewed by the app whenever you post.

Instagram's API takes feed shapes from 4:5 to 1.91:1, so a 9:16 crop has to go
through Share instead.

A post made straight to Instagram can also carry two things Share cannot:

- **People tagged on the photo.** Type a name under the Instagram hashtags and
  choose Tag. The tag appears over the photo; tap its name and then the photo
  to put it where the person is. Up to 20.
- **Collaborators.** Up to three accounts, each of whom gets an invitation to
  show the post on their own profile once it is out.

Both are sent with the post as Meta's publishing API describes. Meta's own
pages disagree about whether an app set up with Unified login may use them, so
the first real post decides: if Instagram refuses a tagged post, nothing is
posted and the message says to check the names or take them off, and if it
posts but leaves the tags off, the app says so.

## Logo shadow

The logo can have a soft drop shadow, set with the Shadow slider under Logo in
Settings: off at zero, darker as it goes up. The app draws it at the finished
image's own size from the logo's exact outline, so it is the same on every
post and works for a vector or a picture. Its size follows the logo's, in the
proportions of a drawing program's drop shadow (a blur of 2.5 and an offset
of 2 on a logo 400 wide).

A shadow drawn into an SVG as a filter is not rendered: the library that
draws SVGs has no filter support, and the app says so when such a file is
chosen. Use the plain logo and the slider instead.

## Removed posts

Removing a post takes it off the list and deletes the image that was made from
it, but keeps its original photo and record on the phone. Settings, Storage
shows how many removed posts are still kept and how much room they take, and
clears them on request. Clearing cannot be undone: photos picked from the
gallery still exist there, but photos taken with the in-app camera do not
exist anywhere else.

## Local settings

The look, the logo placement, the usual crop shape and the caption snippets are
stored on the phone behind the shared `LocalSettingsRepository` abstraction.
Each post's framing is kept by `PhotoTreatmentStore`.

The AI service address and the model list per AI operation live with the other
settings. The API key is kept separately in `EncryptedSharedPreferences`,
backed by an Android Keystore `MasterKey`; settings only ever shows a masked
hint of it and lets you replace or remove it. The Bluesky app password, the
Instagram access token and the Firebase upload password are stored the same
way. If the phone's secure storage cannot be opened, the app refuses to save
them and says so in Settings; they are never written without encryption. If
what was saved can no longer be read, it is cleared and Settings says that
too. No account details live in this repository: everything is entered on the
phone, including which Firebase project to use.
