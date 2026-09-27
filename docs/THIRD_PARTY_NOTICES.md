# Third-party notices

This inventory was generated from the resolved `releaseRuntimeClasspath` on 2026-09-27. It contains 79 unique runtime artifacts after duplicate edges were removed. Every runtime artifact listed below is licensed under the Apache License 2.0. The complete text is in [`licenses/APACHE-2.0.txt`](licenses/APACHE-2.0.txt) and is also packaged in the APK as `META-INF/LICENSE-MOSSLIGHT-THIRD-PARTY.txt`. The machine-readable inventory is [`DEPENDENCY_INVENTORY.json`](DEPENDENCY_INVENTORY.json).

The resolved release artifacts contained no `NOTICE`, `NOTICE.txt`, `NOTICE.md`, or `DEPENDENCIES` entry that required an additional upstream notice. Some artifacts carry module-specific Apache license files; because Android packaging may merge duplicate `META-INF` resources, Mosslight Studio also packages a consolidated license and runtime notice at `META-INF/LICENSE-MOSSLIGHT-THIRD-PARTY.txt` and `META-INF/NOTICE-MOSSLIGHT-THIRD-PARTY.txt`.

## Release runtime artifacts

### AndroidX and Jetpack

Copyright The Android Open Source Project and contributors. Project: [AndroidX](https://github.com/androidx/androidx). License: Apache-2.0.

| Group and version | Resolved artifact modules |
|---|---|
| `androidx.activity:1.13.0` | `activity`, `activity-compose`, `activity-ktx` |
| `androidx.annotation:1.4.1` | `annotation-experimental` |
| `androidx.annotation:1.9.1` | `annotation-jvm` |
| `androidx.arch.core:2.2.0` | `core-common`, `core-runtime` |
| `androidx.autofill:1.0.0` | `autofill` |
| `androidx.collection:1.5.0` | `collection-jvm`, `collection-ktx` |
| `androidx.compose.animation:1.10.6` | `animation-android`, `animation-core-android` |
| `androidx.compose.foundation:1.10.6` | `foundation-android`, `foundation-layout-android` |
| `androidx.compose.material:1.10.6` | `material-ripple-android` |
| `androidx.compose.material3:1.4.0` | `material3-android` |
| `androidx.compose.runtime:1.10.6` | `runtime-android`, `runtime-annotation-android`, `runtime-retain-android`, `runtime-saveable-android` |
| `androidx.compose.ui:1.10.6` | `ui-android`, `ui-geometry-android`, `ui-graphics-android`, `ui-text-android`, `ui-unit-android`, `ui-util-android` |
| `androidx.concurrent:1.1.0` | `concurrent-futures` |
| `androidx.core:1.0.0` | `core-viewtree` |
| `androidx.core:1.18.0` | `core`, `core-ktx` |
| `androidx.customview:1.0.0` | `customview-poolingcontainer` |
| `androidx.datastore:1.2.1` | `datastore-core-android`, `datastore-core-okio-jvm` |
| `androidx.documentfile:1.0.0` | `documentfile` |
| `androidx.dynamicanimation:1.0.0` | `dynamicanimation` |
| `androidx.emoji2:1.4.0` | `emoji2` |
| `androidx.graphics:1.0.1` | `graphics-path` |
| `androidx.interpolator:1.0.0` | `interpolator` |
| `androidx.legacy:1.0.0` | `legacy-support-core-utils` |
| `androidx.lifecycle:2.9.4` | `lifecycle-common-java8`, `lifecycle-common-jvm`, `lifecycle-livedata`, `lifecycle-livedata-core`, `lifecycle-livedata-core-ktx`, `lifecycle-process`, `lifecycle-runtime-android`, `lifecycle-runtime-compose-android`, `lifecycle-runtime-ktx-android`, `lifecycle-viewmodel`, `lifecycle-viewmodel-android`, `lifecycle-viewmodel-compose-android`, `lifecycle-viewmodel-ktx`, `lifecycle-viewmodel-savedstate-android` |
| `androidx.loader:1.0.0` | `loader` |
| `androidx.localbroadcastmanager:1.0.0` | `localbroadcastmanager` |
| `androidx.navigationevent:1.0.0` | `navigationevent-android`, `navigationevent-compose-android` |
| `androidx.print:1.0.0` | `print` |
| `androidx.profileinstaller:1.4.0` | `profileinstaller` |
| `androidx.savedstate:1.3.2` | `savedstate-android`, `savedstate-compose-android`, `savedstate-ktx` |
| `androidx.startup:1.1.1` | `startup-runtime` |
| `androidx.tracing:1.2.0` | `tracing` |
| `androidx.transition:1.6.0` | `transition` |
| `androidx.versionedparcelable:1.1.1` | `versionedparcelable` |
| `androidx.window:1.5.0` | `window`, `window-core-android` |

### Other runtime projects

| Resolved coordinates | Project and attribution | License |
|---|---|---|
| `com.google.code.gson:gson:2.14.0` | [Gson](https://github.com/google/gson), Google and contributors | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations:2.48.0` | [Error Prone](https://github.com/google/error-prone), Google and contributors | Apache-2.0 |
| `com.google.guava:listenablefuture:1.0` | [Guava](https://github.com/google/guava), Google and contributors | Apache-2.0 |
| `com.squareup.okio:okio-jvm:3.9.1` | [Okio](https://github.com/square/okio), Square and contributors | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.3.20` | [Kotlin](https://github.com/JetBrains/kotlin), JetBrains and Kotlin contributors | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`; `kotlinx-coroutines-core-jvm:1.10.2` | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), JetBrains and contributors | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3`; `kotlinx-serialization-json-jvm:1.7.3` | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), JetBrains and contributors | Apache-2.0 |
| `org.jetbrains:annotations:23.0.0` | [JetBrains Java Annotations](https://github.com/JetBrains/java-annotations), JetBrains and contributors | Apache-2.0 |
| `org.jspecify:jspecify:1.0.0` | [JSpecify](https://github.com/jspecify/jspecify), JSpecify authors | Apache-2.0 |

## Development and test dependencies

The following artifacts are resolved only by debug or test configurations and are not part of the release runtime artifact set.

| Coordinates or family | License | License text / source |
|---|---|---|
| AndroidX Test: `core:1.7.0`, `core-ktx:1.7.0`, `monitor:1.8.0`, `runner:1.7.0`; Test Ext JUnit `1.3.0`; Test Services Storage `1.6.0`; Concurrent Futures KTX `1.2.0` | Apache-2.0 | [`APACHE-2.0.txt`](licenses/APACHE-2.0.txt), [AndroidX](https://github.com/androidx/androidx) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2` and `kotlinx-coroutines-test-jvm:1.10.2` | Apache-2.0 | [`APACHE-2.0.txt`](licenses/APACHE-2.0.txt), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| `junit:junit:4.13.2` | EPL-1.0 | [`EPL-1.0.txt`](licenses/EPL-1.0.txt), extracted from the resolved JAR |
| `org.hamcrest:hamcrest-core:1.3` | BSD-3-Clause | [`HAMCREST-BSD-3-Clause.txt`](licenses/HAMCREST-BSD-3-Clause.txt), extracted from the resolved JAR |

## Build tools distributed or declared with the source

| Tool | Version | Distribution status | License |
|---|---|---|---|
| Gradle wrapper JAR and scripts | `9.1.0` | Wrapper JAR is included in the source tree; the distribution is downloaded from the checksum-pinned official URL | Apache-2.0; the JAR retains `META-INF/LICENSE` |
| Android Gradle Plugin | `9.0.1` | Declared build plugin; downloaded by Gradle and not packaged in the app | Apache-2.0 |
| Kotlin Compose compiler Gradle plugin | `2.3.20` | Declared build plugin; downloaded by Gradle and not packaged in the app | Apache-2.0 |

The resolved buildscript graph contains 114 coordinates and is intentionally excluded from the app's runtime notice because those artifacts are build inputs rather than redistributed app components. The review inventory records that graph separately so a source-distribution auditor can reproduce it.

## Build-input provenance note

The non-redistributed buildscript graph includes `com.google.code.findbugs:jsr305:3.0.2`. Its Maven metadata labels it Apache-2.0, while the upstream FindBugs repository separately carries a BSD notice for JSR-305 annotation sources and LGPL terms for other FindBugs code; the resolved JAR contains no embedded license. This does not affect the release APK or either test runtime after unused Compose test tooling was removed. Anyone redistributing that build artifact itself should resolve its source-to-binary provenance and include the applicable upstream text rather than relying only on the metadata label.

No unresolved license or notice requirement was found for the resolved release or test runtime artifacts. Re-run the inventories and APK `META-INF` comparison whenever dependency versions change.
