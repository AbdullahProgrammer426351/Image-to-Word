# V2 Hardening Milestone

Implemented on `feature/backend-account-integration` without changing the published `main` branch.

The published `main` branch now calls the separate legacy `imageToWordfree` function. This branch intentionally keeps its V2 path on `imageToWordV2`; the legacy endpoint change is not ported into V2.

## Completed

- V2-enabled production environments now require the approved Firebase project, Android App ID, exact Functions base URL, HTTPS, no Auth emulator, and the `production-internal` environment name.
- Emulator builds require the demo Firebase project/App ID, emulator endpoints, and production-service isolation.
- The ordinary release build remains V2-disabled and cannot carry a backend URL.
- Conversion entry waits for an authoritative account projection. Loading and temporary account failures show a retry path; `FREE`, `TEMPORARY_UNLIMITED`, `CREDIT_BASED`, and `NONE` remain server-defined.
- Pending purchase decoding ignores malformed individual records without discarding valid records and collapses duplicate tokens.
- Production diagnostics no longer emit credit balances or subscription metadata; authentication/App Check/purchase tokens remain excluded.
- Added focused environment, state-machine, parser, persistence, and diagnostic regression tests.
- Added concurrent-refresh serialization, verifier-recreation recovery, interrupted transport/server-error handling, and multi-image partial-result tests.
- Physical-device integration builds now remove stale generated Google Services resources when the local emulator configuration intentionally skips the Google Services task.
- The instrumentation fixture asserts the active variant application ID and resets only local emulator Auth sessions before emulator tests.

## Remaining gaps

- A Play-distributed build is still required for live Play Integrity/App Check validation.
- Compose/UI coverage for account banners and retry dialogs remains limited; state-machine coverage is currently unit-level.
- Android process-death instrumentation and end-to-end concurrent purchase/account-refresh tests remain future work; verifier recreation is covered with a local fake store.
- The internal production variant shares the published package ID; installing it updates the published app. Keep internal testing isolated operationally until a separate package strategy is approved.
- The Firebase worker incompatibility is resolved by backend commit `a201cdd`: physical-device testing must use the repository-local Firebase CLI 15.33.0 (or newer), never the globally installed 14.20.0 CLI.

## Physical-device integration run (2026-10-16)

- Device `34255100230004F` was detected with both the published package and the separate `.integrationtest` package present. The production package was not uninstalled, cleared, or replaced.
- Auth, Firestore, Functions, and Pub/Sub emulators were started with demo project `demo-converter-credit`; ADB reverse mappings were restored for ports 9099, 8080, 5001, and 8085. The documented emulator credit settings were seeded, and Functions reported outbound network stubbing.
- The connected suite executed five tests: two passed, the fake-Play test was skipped because no Android fake fixture arguments were provided, and Auth/bootstrap plus V2 conversion/idempotency timed out. Emulator logs show the worker is killed on the request path after the `firebase-functions` v7 `functions.config()` removal warning. This remains a backend blocker for the Android end-to-end run; no Android workaround or backend edit was made.
- Android unit tests and Debug/Internal/Release Kotlin compilation passed. The integration APK built and installed successfully as `com.image.word.converter.convert.docx.integrationtest` (versionCode 8); the published package remained `com.image.word.converter.convert.docx`.

## Completed physical-device validation (2026-10-16)

- Repeated the run with Java 21, repository-local Firebase CLI 15.33.0, demo project `demo-converter-credit`, fake provider mode, fake Google Play mode, and local Auth/Firestore/Functions/Pub/Sub only. Authenticated HTTP workers remained alive and all backend milestone suites passed.
- Added emulator-only weekly, monthly, purchase-recovery, and conversion-credit fixtures. Their seeder refuses non-demo projects and non-local Firestore hosts; the Android APK contains no fake purchase bypass or token.
- The physical-device suite passed 6/6 with no skips: application-ID isolation, Auth/bootstrap, lifetime-free conversion and same-key replay, paid 5-credit minimum charging and replay, weekly 500-credit verification, monthly 1,200-credit verification, duplicate callbacks/restores, authoritative account refresh, and persisted recovery with a malformed sibling record.
- Corrected the instrumentation expectation that predated the lifetime-free policy. The test now proves the first successful conversion is free, then verifies a separate fake weekly purchase before asserting the 5-credit minimum and no duplicate charge.
- Full operating-system kill/relaunch orchestration remains outside this suite. Device instrumentation exercises the real private preference store and verifier recreation; JVM regression tests continue to cover process recreation, concurrent refresh, interrupted transport, partial batches, and redaction.

## Deferred by billing decision

No payment-source abstraction, one-time product, product-ID, entitlement, or existing-subscriber policy changes were made. Any redesign of purchase restoration or entitlement mapping must wait for the subscription versus one-time credit decision.
