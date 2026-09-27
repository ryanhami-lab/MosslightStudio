# Building a release

The default release is unsigned:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

For a signed build, create a keystore dedicated to this application outside the source tree and supply these environment variables to the build process:

| Variable | Meaning |
| --- | --- |
| `MOSSLIGHT_STORE_FILE` | Absolute path to the application's keystore |
| `MOSSLIGHT_STORE_PASSWORD` | Keystore password |
| `MOSSLIGHT_KEY_ALIAS` | Signing alias; defaults to `mosslight` |
| `MOSSLIGHT_KEY_PASSWORD` | Private key password |

Use your password manager or a local secret loader; do not put values into source, command history, screenshots, CI logs, Gradle properties, or a README. Keep a secure backup of the release signer if you intend to publish updates. The generated local debug signer is not a release signer.

## Release review

1. Review the complete source/media allowlist and the actual diff. Exclude local configuration, signing files, caches, reports containing local paths, and intermediate outputs.
2. Build from that source allowlist in a separate directory with no private input. Record Java, Android SDK and wrapper versions. Inspect the resolved dependencies and their notices.
3. Run unit tests, lint, and the device checks in [TESTING.md](TESTING.md). Review actual screens at normal and large text sizes; record limitations.
4. Inspect the merged release manifest and built APK: application ID, non-debuggable flag, permissions, exported components, assets, DEX/resource strings, native libraries, ZIP paths, licenses, and signing certificate.
5. Inspect each published screenshot or recording and its metadata. Check all Git objects, author identity, branch, remotes, commit messages, filenames, and automation. A filename scan alone is insufficient.
6. Record SHA-256 hashes for the exact source archive, APK, and media. A change to any candidate file invalidates its prior hash and requires review of the changed surface.
7. Confirm that the publisher owns or has authority to relicense the adapted prior-work primitives and all newly generated code and code-authored artwork under MIT; record any required attribution or exclude material whose rights cannot be confirmed.
8. Obtain approval of that exact candidate and destination before pushing, creating a remote repository, or uploading release/media files.

`tools/audit_candidate.py` supports an optional private denylist stored outside the project and APK scanning. Its output does not echo denylist values. This is a bounded check, not proof that no sensitive or recognizable information remains. Human review of meaning, visual resemblance, ownership, and intended public identity is still required.

There is no upload workflow or configured remote in the initial local candidate. Publication is a separate, deliberate action.
