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

## Firebase App Check

Non-emulator builds install the official Play Integrity App Check provider during `Application.onCreate`, before this application accesses Firebase Auth, Analytics, or Crashlytics. The V2 HTTP client obtains a cached App Check token for each authenticated request and sends it as `X-Firebase-AppCheck`; the Firebase Auth `Authorization: Bearer` header remains independent and unchanged.

The client performs at most one forced App Check refresh, and only after initial SDK token acquisition fails or the backend returns `403 APP_CHECK_FAILED`. A repeated acquisition failure becomes a typed, retryable `BackendFailure.AppCheck`; no backend request is sent without the required production token. Request diagnostics redact both credential headers and request bodies, and application logs contain neither Auth nor App Check tokens.

The `demo-converter-credit` emulator environment uses `DisabledAppCheckSession`: it neither initializes Play Integrity nor sends a fabricated App Check token. This preserves local Auth/Functions emulator tests while backend App Check is in monitor-only mode. There is no debug-provider dependency or hardcoded debug token in the application. Release still installs Play Integrity but cannot make a production V2 request while `BACKEND_ACCOUNT_INTEGRATION_ENABLED=false` and the Functions URL is empty. Backend enforcement is not controlled or enabled by Android.

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

The backend-selected `IMAGE_TO_WORD_APP` / `image-to-word` wallet, pricing, reservations, access mode, charge, result, and balance are authoritative. Android never estimates, grants, deducts, or edits credits. The account projection now includes the exact `conversionAccess` object (`mode`, `canConvert`, and `freeConversion.status/available`). The home interface renders FREE, TEMPORARY_UNLIMITED, CREDIT_BASED, or NONE from that projection and refreshes account state after every conversion batch, including failures.

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

## Backend-verified Google Play subscriptions

Billing Library 9.1.0 queries only `imagetoword_1w` and `imagetoword_1m`. A V2-enabled purchase follows this sequence:

1. Google Play returns a `PURCHASED` subscription with its actual product ID and purchase token.
2. Android persists that exact pair in app-private storage before verification.
3. The authenticated backend client sends exactly `productId` and `purchaseToken` to `verifySubscriptionPurchaseV2`.
4. The backend verifies Play ownership/state, grants any eligible period credits idempotently, and owns acknowledgement.
5. Android accepts the returned account projection and immediately refreshes `getAccountStateV2`.
6. Subscription UI, premium indicators, ads, and credits read the authoritative account repository.

Play `PURCHASED` by itself never sets `isSubscribed` and never adds credits. `PENDING` is displayed as pending and is not verified as active. Android does not acknowledge in V2 mode. Backend statuses `ACTIVE`, `GRACE_PERIOD`, and `CANCELLED` remain entitled only through the authoritative `periodEnd`; `EXPIRED`, `ON_HOLD`, `PAUSED`, `PENDING`, and `REVOKED` are not active. Loading or degraded repository states retain the previous authoritative account for UI continuity instead of declaring a verified subscriber expired.

### Subscription-transition compatibility audit

The existing and future credit subscriptions reuse the same Play products:

- Weekly: `imagetoword_1w`
- Monthly: `imagetoword_1m`

There are no proposed product IDs, local credit grants, or local grandfathering heuristics in Android. In V2, a `PURCHASED` callback is converted to a product/token candidate and passed to backend verification; only the returned account projection can activate subscription UI. Android does not infer an existing subscriber from app version, install date, purchase age, or local Billing state.

The production-disabled legacy path is intentionally unchanged: a restored `PURCHASED` weekly/monthly item sets the local subscribed flag and Android acknowledges it. `queryPurchasesAsync(SUBS)` runs after initial Billing setup/reconnection, explicit Restore, and app resume; in V2 the same restored product/token is submitted to the backend instead of granting local entitlement.

The finalized V2 projection exposes `conversionAccess.mode` and `freeConversion.available`. Android renders temporary unlimited access without claiming lifetime entitlement, renders the one-time free allowance without presenting artificial credits, and renders the authoritative numeric balance only for CREDIT_BASED. The generic `isSubscribed` flag continues to drive premium/ad UI; it does not grant conversion access or bypass the backend.

After the backend response contract is finalized, Android needs one server-derived conversion-access model with at least these renderable states:

- free conversion available (one) or already used (zero), without presenting the free use as a credit balance;
- temporary grandfathered unlimited access, with a non-lifetime unlimited label and no client-side deduction;
- metered subscriber credits using the authoritative balance;
- loading or temporarily unavailable state that retains prior authority without inventing access.

The Android model now consumes those finalized fields in the account parser, home banner, and subscription screen. Conversion remains server-authoritative: V2 does not submit an entitlement mode, locally deduct credits, or fall back to the legacy endpoint. The backend still decides permission, access mode, and charges.

### Restore and recovery

`queryPurchasesAsync` runs after Billing setup/reconnection, explicit Restore, and app resume. Current purchased weekly/monthly tokens are submitted to the backend even for existing subscribers upgrading to V2. Duplicate callbacks within one Play result are collapsed; later duplicate restores safely submit the same token because the backend grant and acknowledgement operations are idempotent.

Network, authentication, invalid-response, internal, and subscription-verification failures receive bounded same-token retries. If still unresolved, the product/token pair remains in app-private `MODE_PRIVATE` preferences, is excluded from cloud backup/device transfer, and is retried on reconnect, app resume, restore, and by a network-constrained `JobScheduler` recovery job in a future non-emulator V2 deployment. Tokens are never logged. Invalid purchases and ownership conflicts are terminal for the current account and are removed from retry storage.

The recovery path is designed around Google's acknowledgement deadline: the backend attempts acknowledgement in the original verification request, Android retries transient backend/acknowledgement failures without changing the token, and persistent recovery does not depend solely on the paywall remaining open.

### Product and offer selection

Android displays Google Play's formatted recurring price and billing period. It selects only an eligible offer whose recurring paid phase matches the selected product (`P1W` for weekly, `P1M` for monthly). Android does not invent or hardcode base-plan IDs. If zero or multiple returned offers match, that product is unavailable rather than silently purchasing the first offer. No yearly product is queried or displayed.

### Release and emulator safety

Release V2 remains disabled. The published-app path retains its existing local Billing/acknowledgement behavior until a separately approved backend rollout; it cannot call a production V2 URL because the release Functions base is empty and integration is disabled.

Debug uses the Firebase emulator and its fake Google Play verifier. The interactive app refuses to forward tokens obtained from the real Play Billing client to that fake verifier. Automated emulator validation supplies an explicitly seeded fake Play token to the instrumentation test. Real purchases are never started automatically, and no production Firebase or Google Play mutation is part of this workflow.

`DailyAttemptManager` remains only for the release/legacy conversion path and is not read or mutated by V2 eligibility or completion.

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

For subscription integration, first seed a fake Google Play fixture in the Firestore emulator using the backend's test harness. Then run only the Billing emulator test with that fixture's non-production product/token pair:

```shell
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.image.word.converter.convert.docx.backend.BackendBillingEmulatorIntegrationTest \
  -Pandroid.testInstrumentationRunnerArguments.fakePlayProductId=imagetoword_1w \
  -Pandroid.testInstrumentationRunnerArguments.fakePlayPurchaseToken=<seeded-fake-token>
```

This validates fake Billing input through Firebase Auth, `verifySubscriptionPurchaseV2`, backend acknowledgement simulation, account acceptance, and `getAccountStateV2`. It is not evidence of real Google Play verification.

Before production rollout, a Play license tester must validate actual weekly and monthly ProductDetails/offers, purchase and pending flows, restore after reinstall/account continuity, cancellation/grace/expiry behavior, backend service-account access, server acknowledgement within Google's deadline, duplicate callbacks, upgrade/linked-token behavior, and recovery from a deliberately interrupted verification. Those tests require a deployed non-production backend connected to the real Play Developer API; real tokens must never be sent to the emulator fake.

## Implemented client components

The backend layer is under `app/src/main/java/com/image/word/converter/convert/docx/backend/`:

- `BackendEnvironment`: compile-time endpoint selection and fail-closed validation
- `AuthSession`: Firebase authentication/token boundary
- `AppCheckSession`: Firebase App Check token boundary with emulator isolation
- `BackendHttpClient`: authenticated account and conversion HTTP operations
- `BackendJsonParser`: typed account, conversion, credit, and structured-error parsing
- `BackendModels`: public projection and conversion models
- `AccountRepository`: centralized authoritative account state and refresh behavior
- `ImageConversionBatchRunner`: bounded per-image execution and same-key retry policy
- `BackendSubscriptionVerifier`: persistent same-token backend verification and authoritative account refresh
- `PrivatePendingPurchaseStore`: app-private recovery state excluded from backup
- `SubscriptionVerificationRetryService`: network-constrained persistent recovery for an enabled non-emulator rollout
- `SubscriptionOfferPolicy`: unambiguous Play-metadata-based weekly/monthly offer selection

Android does not access Firestore directly. The backend owns subscription verification, credit allocation, reconciliation, and acknowledgement.
