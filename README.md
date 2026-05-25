# ShotQuill

AI-powered photo-to-post assistant for social media.

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
verified by `./gradlew koverVerify`. The current line-coverage gate is **66%**,
matching the skeleton's baseline. Ratchet it upward with each PR that adds real
domain, repository, prompt, or orchestration code until it reaches **80%**. New
code of that kind must ship with tests in the same PR.

Reports are written to:

- `build/reports/kover/html/index.html` (human-readable)
- `build/reports/kover/report.xml` (machine-readable, for coverage badges/tools)
