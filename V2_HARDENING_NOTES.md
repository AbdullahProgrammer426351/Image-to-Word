# V2 Hardening Milestone

Implemented on `feature/backend-account-integration` without changing the published `main` branch.

## Completed

- V2-enabled production environments now require the approved Firebase project, Android App ID, exact Functions base URL, HTTPS, no Auth emulator, and the `production-internal` environment name.
- Emulator builds require the demo Firebase project/App ID, emulator endpoints, and production-service isolation.
- The ordinary release build remains V2-disabled and cannot carry a backend URL.
- Conversion entry waits for an authoritative account projection. Loading and temporary account failures show a retry path; `FREE`, `TEMPORARY_UNLIMITED`, `CREDIT_BASED`, and `NONE` remain server-defined.
- Pending purchase decoding ignores malformed individual records without discarding valid records and collapses duplicate tokens.
- Production diagnostics no longer emit credit balances or subscription metadata; authentication/App Check/purchase tokens remain excluded.
- Added focused environment, state-machine, parser, persistence, and diagnostic regression tests.
- Added concurrent-refresh serialization, verifier-recreation recovery, interrupted transport/server-error handling, and multi-image partial-result tests.

## Remaining gaps

- A Play-distributed build is still required for live Play Integrity/App Check validation.
- Compose/UI coverage for account banners and retry dialogs remains limited; state-machine coverage is currently unit-level.
- Android process-death instrumentation and end-to-end concurrent purchase/account-refresh tests remain future work; verifier recreation is covered with a local fake store.
- The internal production variant shares the published package ID; installing it updates the published app. Keep internal testing isolated operationally until a separate package strategy is approved.

## Deferred by billing decision

No payment-source abstraction, one-time product, product-ID, entitlement, or existing-subscriber policy changes were made. Any redesign of purchase restoration or entitlement mapping must wait for the subscription versus one-time credit decision.
