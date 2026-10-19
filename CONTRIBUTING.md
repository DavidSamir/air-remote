# Contributing

Keep the main app to one button. Put transport/protocol work behind the existing service boundary and explain any behavior change in the pull request.

1. Follow [Building](docs/BUILDING.md), then run `python scripts/build.py`.
2. Keep app Kotlin under `app/src/main/kotlin/dev/airremote` and JVM tests under `app/src/test/kotlin/dev/airremote`.
3. Follow `.editorconfig`. Kotlin sources are formatted with ktlint 1.5.0 using the Android Studio style.
4. Add meaningful protocol tests when changing wire encoding or parsing. Use the hardware checklist for connection lifecycle changes.
5. Explain validation and limits in the PR. Do not claim uninterrupted Google Health syncing without the connection/sync evidence described in [Testing](docs/TESTING.md).

Dependency updates belong in `dependencies.lock.json`, with provenance/license updates and a build check. Do not commit `.deps`, SDK downloads, `.gradle`, build output, APKs, keystores, personal paths, phone serials, or runtime logs.

Limit formatting to this app's source paths; do not format the generated upstream checkout under `.deps/`.

This folder is designed to be the root of a separate public repository. A branch in an existing repository inherits that repository's history; publishing it can expose unrelated files. Export only this project folder or create folder-only history before publishing.

Contributions are provided under this project's Apache-2.0 license. Retain upstream notices and identify modified upstream files.
