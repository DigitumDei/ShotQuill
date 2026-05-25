# Contributing to ShotQuill

ShotQuill is a Kotlin Multiplatform / Compose Multiplatform project with an
Android-first MVP. The repo is intentionally set up so contributors can work
from lightweight machines (including Codespaces / cloud dev VMs) that cannot
practically build Android locally.

## Validation is done in CI

The canonical "did it build / did tests pass / did coverage hold" gate is
**GitHub Actions**, defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

CI runs on every pull request and every push to `main` and executes:

| Step              | Command                  | Fails the build when                   |
| ----------------- | ------------------------ | -------------------------------------- |
| Compile           | `./gradlew build`        | Any module fails to compile            |
| Unit tests        | `./gradlew test`         | Any unit test fails                    |
| Coverage gate     | `./gradlew koverVerify`  | Line coverage drops below the threshold |

Coverage HTML/XML reports are uploaded as workflow artifacts on every run so
you can inspect them without re-running locally.

## Suggested workflow from a lightweight machine

1. Branch off `main`.
2. Make your changes.
3. Push the branch and open a draft PR.
4. Iterate against the CI signal — fix red checks, push again.
5. Mark "Ready for review" once CI is green.

## Tests are required for real code

Per issue #1, the coverage gate exists from day one. As you add new
**domain, repository, prompt, or orchestration** code, include unit tests in
the same PR. The coverage threshold will be raised over time as production
code lands; do not regress it.

The initial threshold is configured in the root [`build.gradle.kts`](build.gradle.kts)
under the `kover { reports { verify { ... } } }` block.

## Running locally (optional)

If your machine can run the Android toolchain, you can mirror CI with:

```bash
./gradlew build
./gradlew test
./gradlew koverVerify
```

You need JDK 17 and the Android SDK (`compileSdk` 35). Otherwise, lean on CI.
