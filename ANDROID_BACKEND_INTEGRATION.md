# Android Backend Integration

This document describes the Phase A Android foundation for the V2 backend. The exact HTTP contract remains in `ANDROID_BACKEND_CONTRACT.md`.

## Environments

Environment selection is compile-time and centralized in `app/build.gradle.kts` plus `BackendEnvironment`.

### Debug

- Firebase project: `demo-converter-credit`
- Functions base: `http://10.0.2.2:5001/demo-converter-credit/us-central1/`
- Auth Emulator: `10.0.2.2:9099`
- V2 account integration: enabled
- Production Remote Config, Analytics, Crashlytics collection, and Ads requests: disabled
- Cleartext HTTP: allowed only for `10.0.2.2` through the debug manifest/network security configuration

`BackendEnvironment` validates these values and fails closed if an emulator build is configured with the production project or a different Functions/Auth endpoint.

### Release

- Firebase project remains `converter-api-project` through the existing Firebase configuration.
- V2 account integration is disabled.
- The V2 Functions base URL is intentionally empty.
- Existing release Firebase, conversion, Billing, Remote Config, Analytics, Crashlytics, and Ads behavior remains active.

Production V2 must be enabled only after an explicit backend and Android production review.

## Authentication and Firebase initialization

`ImageToWordApplication` runs before `MainActivity` and configures the debug Auth Emulator before account integration uses Firebase Auth.

Debug account integration uses a dedicated named Firebase app configured for `demo-converter-credit`. The legacy default Firebase Auth singleton is also directed to the Auth Emulator in debug as a safety measure, preventing accidental production authentication during local testing. ID tokens are never logged.

`FirebaseAnonymousAuthSession`:

1. Reuses `currentUser` when present.
2. Signs in anonymously when no user exists.
3. obtains an ID token for each backend request.
4. Supports one forced token refresh after a `403 UNAUTHENTICATED` response.

## Backend client

The backend layer is under:

`app/src/main/java/com/image/word/converter/convert/docx/backend/`

- `BackendEnvironment`: build-time endpoint selection and fail-closed validation.
- `AuthSession`: reusable Firebase authentication/token boundary.
- `BackendHttpClient`: authenticated HTTP transport and V2 account operations.
- `BackendJsonParser`: typed account and structured-error parsing.
- `BackendModels`: backend public projection models.
- `AccountRepository`: centralized in-memory account state and refresh behavior.

The current client implements:

- `bootstrapUserV2`
- `getAccountStateV2`

Endpoint names for `imageToWordV2` and `verifySubscriptionPurchaseV2` are centralized for later phases, but no conversion or Billing flow calls them yet.

Every implemented request attaches `Authorization: Bearer <Firebase ID token>`. Bootstrap sends exactly `{}`. Android never supplies `userId`.

## Account models and state

The typed projection consists of:

- `BackendUser`
- `CreditsState`
- `SubscriptionState`
- `AccountState`
- `BackendError`

Nullable subscription fields remain nullable. ISO-8601 timestamps remain server-provided strings; Android does not calculate expiry.

`AccountRepositoryState` exposes:

- `Loading`
- `Ready`
- `Degraded`
- `Disabled` for builds where V2 integration is intentionally unavailable

A degraded state may retain the last in-memory account solely for UI continuity. It is never treated as permanent entitlement authority.

## Startup flow

For debug builds:

1. `ImageToWordApplication` isolates unrelated production services.
2. The Auth Emulator is configured.
3. Anonymous authentication is ensured.
4. `bootstrapUserV2` is called.
5. Its complete account projection becomes `AccountRepositoryState.Ready`.

Bootstrap already returns the complete account state, so startup does not make a redundant `getAccountStateV2` request. `AccountRepository.refresh()` uses `getAccountStateV2`; if it receives `404 ACCOUNT_NOT_FOUND`, it bootstraps and updates the state.

Failures become `Degraded` and do not block the existing Compose UI. Debug Logcat diagnostics use the tag `BACKEND_ACCOUNT` and report only environment, readiness, credit count, and subscription status—never tokens or secrets.

## Running locally

Start the backend emulators for project `demo-converter-credit` with at least:

- Auth Emulator on port `9099`
- Functions Emulator on port `5001`
- Any server-side emulator dependencies required by the backend project

Then build/install the Android debug variant and launch it on the Android Emulator. In Logcat, filter for:

```text
BACKEND_ACCOUNT
```

A successful run reports:

```text
backendEnvironment=emulator, project=demo-converter-credit
authenticated=true, backendUserResolved=true, availableCredits=..., subscriptionStatus=...
```

Run local unit tests with:

```shell
./gradlew testDebugUnitTest
```

With the backend emulators running and an Android Emulator connected, run the end-to-end instrumentation test with:

```shell
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.image.word.converter.convert.docx.backend.BackendEmulatorIntegrationTest
```

This test performs real anonymous Auth Emulator sign-in and calls `bootstrapUserV2`; it does not substitute a fake identity.

## Intentionally legacy after Phase A

The following behavior is intentionally unchanged:

- `ImageToWordApi` still calls the original production `imageToWord` endpoint and sends only its legacy image body.
- `SubscriptionViewModel` still owns the current Google Play Billing behavior and local subscription state.
- Android still acknowledges purchases locally.
- `DailyAttemptManager` still controls the current local free attempt.
- Current premium tool locks and ad entitlement behavior remain unchanged.
- App Check is not enabled or enforced.
- Android does not access Firestore.

These areas move to backend authority only in their later migration phases.
