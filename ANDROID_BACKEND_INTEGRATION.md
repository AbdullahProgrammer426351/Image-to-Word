# Android Backend Integration

This document describes the Android foundation and credit-based Image-to-Word conversion integration. The exact public HTTP contract remains in `ANDROID_BACKEND_CONTRACT.md`.

## Environment and rollout boundary

Environment selection is compile-time and centralized in `app/build.gradle.kts` and `BackendEnvironment`.

### Debug

- Firebase project: `demo-converter-credit`
- Functions base: `http://10.0.2.2:5001/demo-converter-credit/us-central1/`
- Auth Emulator: `10.0.2.2:9099`
- V2 account and conversion integration: enabled
- Production Remote Config, Analytics, Crashlytics collection, and Ads requests: disabled
- Cleartext HTTP: allowed only for `10.0.2.2` by the debug network-security configuration

`BackendEnvironment` validates these values and fails closed if an emulator build is configured with the production project or a different Functions/Auth endpoint.

### Release

- Firebase project remains `converter-api-project` through the existing Firebase configuration.
- V2 account and conversion integration is disabled.
- The V2 Functions base URL is intentionally empty.
- Image conversion follows the pre-existing legacy implementation.
- Existing release Firebase, Billing, Remote Config, Analytics, Crashlytics, and Ads behavior remains active.

There is no runtime fallback from an enabled V2 conversion to the legacy endpoint. Production V2 must be enabled only after the backend functions, configuration, rules, credentials, App Check policy, and deployment have been reviewed and intentionally deployed.

## Authentication and startup account state

`ImageToWordApplication` configures the debug Auth Emulator before account integration uses Firebase Auth. Debug integration uses a dedicated named Firebase app for `demo-converter-credit`; the default Firebase Auth singleton is also directed to the emulator as a safety measure. ID tokens are never logged.

`FirebaseAnonymousAuthSession` reuses the current anonymous user or signs in anonymously, obtains an ID token for every backend request, and supports one forced token refresh following `403 UNAUTHENTICATED`.

On debug startup, `AccountRepository` authenticates and calls `bootstrapUserV2`. The returned account projection becomes the authoritative in-memory UI state. `refresh()` calls `getAccountStateV2` and bootstraps if the backend reports `404 ACCOUNT_NOT_FOUND`.

Repository states are `Loading`, `Ready`, `Degraded`, and `Disabled`. A degraded state may retain the last account for display continuity, but it is never conversion or entitlement authority.

## V2 Image-to-Word conversion

The enabled V2 flow calls `imageToWordV2` with an authenticated request containing exactly:

```json
{
  "image": "canonical-standard-base64-jpeg",
  "idempotencyKey": "stable-per-image-uuid"
}
```

Android JPEG-encodes each selected bitmap once, uses standard Base64 with padding and no whitespace, and retains both the encoded payload and UUID for the lifetime of that logical attempt. It does not send a user ID, wallet/application identifier, decoded size, price, charge, or balance.

The backend-selected `IMAGE_TO_WORD_APP` / `image-to-word` wallet, pricing, reservations, charge, result, and balance are authoritative. Android never estimates, grants, deducts, or edits credits. The home interface displays `AccountRepository`'s available balance and refreshes account state after every conversion batch, including failures.

The conversion transport allows up to 120 seconds for the provider-backed request. A timeout is treated as ambiguous rather than a definitive conversion failure.

## Batch and retry behavior

There is no client-side batch endpoint. A selection of N images creates N independent request states and N independent idempotency keys. At most two calls run concurrently.

Automatic retries are bounded to two retries and occur only for transport ambiguity or `CONVERSION_PENDING`. Every automatic and user-initiated retry reuses the exact original request object, payload, and key. Independent successes are retained when another item fails, and the user can continue with partial results.

Terminal validation, credit, disabled, and provider-failure responses are not automatically retried. The enabled V2 path never invokes `ImageToWordApi`, so an error cannot silently fall back to an unlimited legacy conversion.

## Error behavior

- `INSUFFICIENT_CREDITS`: show the backend-provided required and available values; refresh account state.
- `UNAUTHENTICATED`: force-refresh an expired token once, then show an authentication error if still rejected.
- `INVALID_BASE64` / `INVALID_REQUEST`: show an invalid-image error.
- `FILE_TOO_LARGE`: show the server-size-limit error and do not retry unchanged.
- `CONVERSION_DISABLED`: show that conversion is unavailable.
- `CONVERSION_FAILED`: show a terminal provider/conversion error for that key.
- `CONVERSION_PENDING`: preserve the original request and offer same-key retry after bounded automatic reconciliation attempts.
- Network timeout/error: describe the outcome as unknown and offer same-key retry.

The result parser accepts the contract's direct result URL and current provider object URL shapes. Successful responses retain the conversion ID, actual charged credits, and returned available balance; `AccountRepository.refresh()` remains the shared home-state reconciliation point.

## Current Billing boundary

Google Play Billing is intentionally not migrated in this milestone. `SubscriptionViewModel` still owns current Play purchase and local subscription behavior, including unrelated premium tool locks and ad behavior.

This creates a temporary product inconsistency: local `isSubscribed` can still affect premium tools and ads, but it cannot bypass backend credits or unlock V2 conversions. Similarly, `DailyAttemptManager` remains for the release/legacy conversion path but is not read or mutated by enabled V2 eligibility or completion. The dedicated Billing milestone must move purchase verification and subscription authority to `verifySubscriptionPurchaseV2` without weakening this conversion boundary.

## Running locally

From the backend repository, start the emulators for `demo-converter-credit` with at least Auth on `9099`, Functions on `5001`, and all server-side emulators required by the backend implementation. Then install and launch the Android debug variant on an Android Emulator.

In Logcat, filter for `BACKEND_ACCOUNT`. A successful bootstrap reports the emulator environment, resolved backend user, available credits, and subscription status without logging tokens.

Run local unit tests and debug compilation with:

```shell
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

With the Firebase emulators running and an Android Emulator connected, run the existing account end-to-end test with:

```shell
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.image.word.converter.convert.docx.backend.BackendEmulatorIntegrationTest
```

The instrumentation test performs a real anonymous Auth Emulator sign-in and calls `bootstrapUserV2`; it never substitutes a production or fake identity. Manual conversion validation should use a small JPEG and confirm a single Conversion record/charge, refreshed home balance, same-key reconciliation after ambiguity, and independent outcomes for a multi-image selection.

## Implemented client components

The backend layer is under `app/src/main/java/com/image/word/converter/convert/docx/backend/`:

- `BackendEnvironment`: compile-time endpoint selection and fail-closed validation
- `AuthSession`: Firebase authentication/token boundary
- `BackendHttpClient`: authenticated account and conversion HTTP operations
- `BackendJsonParser`: typed account, conversion, credit, and structured-error parsing
- `BackendModels`: public projection and conversion models
- `AccountRepository`: centralized authoritative account state and refresh behavior
- `ImageConversionBatchRunner`: bounded per-image execution and same-key retry policy

Android does not access Firestore directly. `verifySubscriptionPurchaseV2` is named in the endpoint registry for the next milestone but is not called yet.
