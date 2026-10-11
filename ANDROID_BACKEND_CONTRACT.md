# Android Backend Contract (V2)

Status: frozen for Android implementation against local emulators. The four HTTP functions in this document are currently guarded to `demo-converter-credit`; they are not production-deployed. The original production `imageToWord` function is unchanged.

## 1. Architecture and source of truth

Android authenticates anonymously with Firebase Authentication and calls HTTPS functions. Functions verify the Firebase ID token, derive the Firebase UID, and own all user, credit, conversion, and subscription decisions. Firestore is server-side only; the Android app does not need the Firestore SDK or direct Firestore access.

Android may cache account state for display, but the cache is never authoritative. Refresh account state after app startup, successful purchase verification, conversion completion, and app foreground/resume when appropriate. Realtime Firestore listeners are not required.

## 2. Environments and base URLs

Local Android Emulator integration uses:

- Firebase project: `demo-converter-credit`
- Functions region: `us-central1`
- Functions Emulator: port `5001`
- Android Emulator host bridge: `10.0.2.2`

| Endpoint | Local Android Emulator URL |
| --- | --- |
| `bootstrapUserV2` | `http://10.0.2.2:5001/demo-converter-credit/us-central1/bootstrapUserV2` |
| `getAccountStateV2` | `http://10.0.2.2:5001/demo-converter-credit/us-central1/getAccountStateV2` |
| `imageToWordV2` | `http://10.0.2.2:5001/demo-converter-credit/us-central1/imageToWordV2` |
| `verifySubscriptionPurchaseV2` | `http://10.0.2.2:5001/demo-converter-credit/us-central1/verifySubscriptionPurchaseV2` |

An eventual production URL has the form `https://us-central1-<project-id>.cloudfunctions.net/<function>`. A production URL must not be enabled in Android until the backend functions, configuration, rules, credentials, App Check policy, and deployment have been reviewed and intentionally deployed.

## 3. Common HTTP contract

| Function | Method | Purpose |
| --- | --- | --- |
| `bootstrapUserV2` | `POST` | Idempotently create/resolve the backend user, grant eligible free credits, and return account state |
| `getAccountStateV2` | `GET` | Return authoritative account state |
| `imageToWordV2` | `POST` | Perform one credit-aware Image-to-Word conversion |
| `verifySubscriptionPurchaseV2` | `POST` | Verify one Play subscription purchase and return authoritative account state |

POST requests use `Content-Type: application/json`. Responses use JSON. Unknown client-supplied authoritative fields are rejected rather than ignored.

### Authentication

Every V2 request requires:

```http
Authorization: Bearer <Firebase ID token>
```

The backend derives the user from the verified token. Android must never send `userId` as authority.

- Missing/empty bearer token: HTTP `401`, code `UNAUTHENTICATED`.
- Invalid, revoked, malformed, or expired token: HTTP `403`, code `UNAUTHENTICATED`.
- On an expired token, Android should force-refresh the Firebase ID token and retry the same idempotent operation once. It must not substitute a client user ID.

### App Check

The prepared header is:

```http
X-Firebase-AppCheck: <token>
```

App Check is not enforced yet. While enforcement is disabled, requests work without the header and a supplied token is not used as authorization. Firebase Authentication remains required independently.

For development, Android should register and use the Firebase App Check debug provider/token only in debug builds. Production will use Play Integrity. When backend enforcement is intentionally enabled, a missing or invalid token returns HTTP `403`, code `APP_CHECK_FAILED`; a valid App Check token still does not replace Firebase Authentication.

### Error envelope

All V2 errors have this stable shape:

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Human-readable, non-authoritative diagnostic"
  }
}
```

The app must branch on `error.code`, not message text. No stack trace, API key, purchase token, provider body, or Firestore implementation detail is returned.

## 4. Account JSON

Account endpoints and successful purchase verification return this public projection. All timestamps are ISO-8601 UTC strings or `null`.

```json
{
  "user": {
    "id": "server-derived-backend-user-id",
    "userName": "Guest",
    "email": null,
    "accountType": "ANONYMOUS"
  },
  "credits": {
    "available": 95,
    "reserved": 0,
    "nextExpiryAt": "2026-10-17T12:00:00.000Z"
  },
  "subscription": {
    "plan": null,
    "status": null,
    "periodEnd": null,
    "autoRenewing": null
  }
}
```

For a subscription, `plan` is currently `WEEKLY` or `MONTHLY`; `status` is an authoritative backend lifecycle state such as `ACTIVE`, `PENDING`, `GRACE_PERIOD`, `ON_HOLD`, `CANCELLED`, `EXPIRED`, `PAUSED`, or `REVOKED`. An `acknowledgementState` field may also be present.

## 5. `bootstrapUserV2`

Request:

```http
POST /bootstrapUserV2
Authorization: Bearer <ID token>
Content-Type: application/json

{}
```

No authoritative user data is accepted. The backend authenticates the caller, idempotently creates or resolves the User, grants the one-time FREE bucket if eligible, and returns:

```json
{
  "success": true,
  "account": { "user": {}, "credits": {}, "subscription": {} }
}
```

Repeated and concurrent calls for the same Firebase UID converge on one backend user, one eligible free bucket, and one GRANT transaction. Errors include `UNAUTHENTICATED`, `INVALID_REQUEST`, `APP_CHECK_FAILED`, `PRICING_UNAVAILABLE`, and `INTERNAL_ERROR`.

## 6. `getAccountStateV2`

Request:

```http
GET /getAccountStateV2
Authorization: Bearer <ID token>
```

Success:

```json
{
  "success": true,
  "account": { "user": {}, "credits": {}, "subscription": {} }
}
```

If the authenticated user has not been bootstrapped, the current local contract returns HTTP `404`, code `ACCOUNT_NOT_FOUND`; Android should call `bootstrapUserV2`, then retry account state.

## 7. `imageToWordV2`

Request:

```json
{
  "image": "<canonical standard Base64 JPEG>",
  "idempotencyKey": "<UUID or other stable unique key>"
}
```

- Method: `POST`.
- `image` is standard Base64 only, with correct padding as needed; data-URI prefixes, whitespace, URL-safe Base64, empty content, and non-canonical encodings are rejected.
- One request contains exactly one JPEG.
- `idempotencyKey` is 1-128 characters from letters, digits, `.`, `_`, `:`, or `-`. A UUID is recommended.
- Android must not send credits, file size, user ID, pricing, or other fields.
- The backend decodes the image, uses the decoded byte count, loads server pricing, and reserves/finalizes credits transactionally.
- Current emulator settings cap the decoded file at 25 MiB. The deployed server configuration is authoritative and may be lower; Android should treat HTTP `413 FILE_TOO_LARGE` as final for that file.

Success (including a completed idempotent retry):

```json
{
  "success": true,
  "result": "https://provider.example/result.docx",
  "conversionId": "server-generated-id",
  "credits": {
    "charged": 5,
    "available": 95
  }
}
```

`result` is the persisted provider result and may be a URL/string or provider result object. A retry with the same user, key, converter, and decoded byte length returns the stored completed result and balance without calling the provider or charging again.

Pending/ambiguous provider or finalization state returns HTTP `202`:

```json
{
  "success": false,
  "error": { "code": "CONVERSION_PENDING", "message": "Conversion requires asynchronous reconciliation" },
  "conversionId": "server-generated-id",
  "status": "EXTERNAL_STATUS_UNKNOWN"
}
```

Do not create a new request automatically after a `202`; poll by retrying the same payload and idempotency key with backoff. A failed/cancelled conversion key returns `CONVERSION_FAILED`; use a new key only for an explicit user retry. Reusing a key for different decoded content/length is invalid and must not be used as a retry strategy.

Insufficient credits returns HTTP `402`:

```json
{
  "success": false,
  "error": { "code": "INSUFFICIENT_CREDITS", "message": "Insufficient credits" },
  "conversionId": "server-generated-id",
  "credits": { "required": 5, "available": 2 }
}
```

### Batch semantics

There is no batch backend endpoint. A selection of N images produces N independent `imageToWordV2` calls. Every image has its own Conversion record, idempotency key, decoded byte size, server credit calculation, reservation, provider call, and finalization.

Android may run a small bounded number concurrently (the existing limit of two is acceptable), but it must retain each image's key and result independently. Firestore reservation transactions already make two concurrent requests for the same user credit-safe: both observe and reserve shared balances transactionally, so total reservations cannot overspend available buckets.

## 8. `verifySubscriptionPurchaseV2`

Request:

```json
{
  "productId": "imagetoword_1m",
  "purchaseToken": "opaque-token-from-Google-Play"
}
```

Android sends only `productId` and `purchaseToken`. It does not send `basePlanId`; the verified Google Play SubscriptionPurchaseV2 line item is sufficient for backend authority. Any attempt to send `userId`, credits, plan/entitlement, period expiry, status, price, renewal state, or other fields is rejected with HTTP `400 INVALID_REQUEST`.

Success:

```json
{
  "success": true,
  "subscription": {
    "plan": "MONTHLY",
    "status": "ACTIVE",
    "periodEnd": "2026-11-10T12:00:00.000Z",
    "autoRenewing": true,
    "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
  },
  "credits": {
    "available": 395,
    "reserved": 0,
    "nextExpiryAt": "2026-10-17T12:00:00.000Z"
  },
  "account": { "user": {}, "credits": {}, "subscription": {} }
}
```

Verification and period grants are idempotent. Retrying the same token does not create duplicate subscription credits. A token already owned by another backend user returns HTTP `409 PURCHASE_TOKEN_OWNERSHIP_CONFLICT`.

### Supported Google Play products

| Product ID | Server plan |
| --- | --- |
| `imagetoword_1w` | `WEEKLY` |
| `imagetoword_1m` | `MONTHLY` |

There is no supported yearly product. Do not query, submit, display, or infer a yearly entitlement until an actual Play product/base-plan mapping is supplied and configured.

The server mapping and grant policy live in `functions/src/config/systemSettings.js` for emulator fixtures and in the server-owned `systemSettings/credits.subscriptionPlans` configuration for an eventual reviewed deployment. Android never supplies grant amounts or expiry policy.

### Purchase acknowledgement and retry ownership

Android launches billing, receives the Play purchase, and sends the token to the backend. It does not grant entitlement and does not normally acknowledge the purchase itself. A Play `PURCHASED` state alone must never mark the user subscribed.

The backend verifies with Google Play, stores/reconciles the Subscription, creates an eligible deterministic credit grant, acknowledges when required, and returns account state. If verification or acknowledgement temporarily fails, Android retains the token and retries `verifySubscriptionPurchaseV2` with bounded exponential backoff and on later app resume. It must not grant local entitlement during the failure and must not replace the token with client-declared subscription fields.

## 9. Error codes and HTTP statuses

| HTTP | Code | Meaning / Android action |
| ---: | --- | --- |
| 200 | — | Successful authoritative response |
| 202 | `CONVERSION_PENDING` | Keep the same conversion key; retry with backoff/reconcile |
| 400 | `INVALID_REQUEST` | Wrong method/body/extra field/idempotency format; do not retry unchanged |
| 400 | `INVALID_BASE64` | Re-encode the JPEG as canonical standard Base64 |
| 400 | `INVALID_PURCHASE` | Bad/mismatched/unsupported verified purchase; do not grant entitlement |
| 401 | `UNAUTHENTICATED` | Missing token; authenticate and retry |
| 402 | `INSUFFICIENT_CREDITS` | Show authoritative balance/purchase path |
| 403 | `UNAUTHENTICATED` | Invalid/expired token; force-refresh once |
| 403 | `APP_CHECK_FAILED` | Obtain a valid App Check token when enforcement is enabled |
| 409 | `PURCHASE_TOKEN_OWNERSHIP_CONFLICT` | Token belongs to another backend user; do not retry as current user |
| 413 | `FILE_TOO_LARGE` | File exceeds server decoded-byte limit |
| 502 | `CONVERSION_FAILED` | Provider/conversion failed; same failed key is terminal; explicit retry uses a new key |
| 503 | `CONVERSION_DISABLED` | Converter/endpoints disabled for this environment |
| 503 | `PRICING_UNAVAILABLE` | Server pricing unavailable; retry later |
| 503 | `SUBSCRIPTION_VERIFICATION_FAILED` | Play verification/acknowledgement unavailable; retry same token later |
| 500 | `INTERNAL_ERROR` | Sanitized unexpected server error; retry only with normal backoff |

Method mismatches return HTTP `405` with code `INVALID_REQUEST`. `ACCOUNT_NOT_FOUND`/404 is limited to account-state-before-bootstrap.

## 10. Android responsibilities

- Use Firebase Anonymous Authentication and send a fresh ID token in every request.
- Generate and persist one stable idempotency key per selected image/attempt.
- Send one canonical Base64 JPEG per conversion request.
- Treat backend account, balance, charge, conversion, and subscription data as authoritative.
- Cache only for UI; refresh at the lifecycle points described above.
- Keep subscription tokens until backend verification succeeds; never infer entitlement from local Play state.
- Do not acknowledge normally once backend verification owns acknowledgement.
- Do not connect to Firestore or calculate/deduct credits locally.
- Do not invent a pre-conversion charge estimate. Phase 1 may display the actual charged credits from the conversion response and refreshed account state. A separate estimate endpoint can be designed later if UX requires one.

## 11. Backend responsibilities

- Verify Firebase Auth for every V2 call and derive identity from the token.
- Optionally verify App Check now, and enforce it only after an intentional production rollout.
- Own user bootstrap, free/paid grants, settings, price calculation, reservations, consumption/release, conversions, subscriptions, acknowledgement, and sanitized errors.
- Use Firestore transactions and deterministic identifiers for concurrency and idempotency.
- Verify Play state from Google; never trust Android entitlement fields.

## 12. Debug and production safety

Debug Android integration must use the Auth Emulator and Functions Emulator under `demo-converter-credit`. Firestore stays behind the functions and is reached only by the local backend. Use build-time environment selection and fail-closed checks so a debug integration build cannot target `converter-api-project` or a production Functions base URL.

Use a separate debug Firebase configuration where practical. Disable or isolate production Remote Config fetch/activation, Analytics, Crashlytics reporting, and Ads traffic in emulator/integration builds so local tests do not mutate production telemetry, experiments, crash dashboards, or ad behavior. These are Android-side recommendations only; this backend milestone changes no Android code.

Before production, replace emulator-only guards deliberately; configure reviewed Firestore settings/rules/indexes, real Google Play service credentials and package access, RTDN/IAM, supported product mappings, Secret Manager, App Check Play Integrity, monitoring, and rollback. Deploy only after an explicit production review. No secret belongs in this contract.
