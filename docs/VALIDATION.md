# Validation for 0.14

## Completed

- APK compiled against API 35 and passed APK signature verification with the existing development signing identity.
- 76 deterministic JVM checks: 23 share-format checks, 23 resolver checks, 11 pickup-state checks and 19 saved-place checks.
- Saved-place checks cover encoding/reload, exact coordinates, Unicode and delimiter handling, duplicate names, deletion, record corruption, bounds and invalid coordinates.
- Resolver fixtures cover a transient short-link 404 that succeeds on retry and a persistent 404 that stops after two attempts. They do not prove that the user's intermittent server response is fixed.
- Java sources formatted with google-java-format 1.24.0; the formatter is a development tool and is not bundled or used at runtime.
- Public source archive excludes signing keys, compiled files, build outputs and local preferences.

## Device checks still required

1. Open 常用地址, add `work`, select a result, name it and save. Close and reopen the app; the address should remain.
2. Choose the saved place and verify its destination in both ride apps.
3. Enter a different address after selecting a result; the previous selection must clear and naming must remain unavailable until a new result is selected.
4. Try a duplicate/empty name; existing places must not be overwritten. Delete one and reopen the app.
5. Open the dialog on the outer and inner displays, with the keyboard open. Search results, name and save control should remain scrollable.
6. Switch to a manual pickup, then share a new Maps place. Pickup should reset to current GPS and the new destination should import.
7. Retry the previously failing Maps link. If it fails, record the exact HTTP/error message. A 404 should remain an error rather than guessing coordinates.

No Android emulator or physical device was available for visual and end-to-end validation of this release. Earlier route handoff and split-screen behavior were tested by the author on their phone. Those results do not substitute for the new feature checks above.
