# Third-party code and notices

Air Remote is licensed under [Apache-2.0](LICENSE). Its app code derives in part from Fitbit Air Notification Bridge and Golden Gate; modified files identify that origin in their headers.

| Component | Source/version | Notices |
| --- | --- | --- |
| Fitbit Air Notification Bridge / Golden Gate fork | Commit in `dependencies.lock.json` | [Original NOTICE](third_party/notices/golden-gate.txt), upstream LICENSE in `.deps/golden-gate/` |
| lwIP | Pinned submodule revision | [COPYING](third_party/notices/lwip.txt) |
| mbedTLS | Pinned submodule revision | [LICENSE](third_party/notices/mbedtls.txt) |
| BitGatt | `com.github.fitbit:bitgatt:v0.9.11` | [Mozilla Public License 2.0](third_party/notices/bitgatt.txt); [upstream source at this tag](https://github.com/Google-Health-API/bitgatt/tree/v0.9.11) |
| AndroidX, RxJava/RxAndroid, Kotlin, Timber, OkHttp | Versions in Gradle configuration | License/notice files in their upstream distributions |
| Gradle wrapper | 6.9.1 | Gradle wrapper source notices and Apache-2.0 distribution |

Transport and native source trees are downloaded into the ignored `.deps/` directory, not copied into app source. Their own license files remain in those checkouts. The preserved Golden Gate NOTICE also lists platform-specific dependencies not used by this Android-only app.

When distributing source or binaries, include the applicable upstream license and notice files alongside the app's LICENSE and NOTICE. Changing the dependency set requires reviewing that set's notices as well.
