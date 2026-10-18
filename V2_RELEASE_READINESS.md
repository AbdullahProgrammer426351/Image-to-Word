# V2 one-time credit release readiness

Status: local Android implementation complete against backend contract commit `8a1906e`; production release remains disabled.

## Completed locally

- Server catalog drives enabled INAPP product IDs, credit amounts, and validity.
- Google Play provides localized product title and price.
- Purchase verification, consumption, grants, expiry, balance, free allowances, ad entitlement, and legacy subscriber entitlement remain server-authoritative.
- Two/one/zero lifetime free conversions and same-key conversion retries are represented without a local permanent counter.
- Outstanding INAPP and legacy SUBS queries are separate; legacy subscriptions are restoration-only and absent from the credit shop.
- Pending verification is private, excluded from backup, deduplicated, retryable after process recreation, and redacted from diagnostics.
- Public release remains V2-disabled; `.integrationtest` remains the physical-device emulator package.

## Required configuration before Play internal testing

- Create and approve the real consumable INAPP product IDs in Play Console; configure the matching production backend catalog with credit amounts and validity. Do not reuse the emulator `test_pack_*` IDs.
- Configure and approve a recoverable Firebase Auth provider and account-linking UX. Purchases remain fail-closed for anonymous identities until this exists.
- Correct the backend `conversionAccess.ads` serialization mismatch and undefined free-conversion counter identifier, then pass backend emulator/acceptance suites.
- Select an approved internal versionCode greater than published versionCode `11`; do not change the public release variant's V2-disabled gate.
- Validate Play Integrity App Check from a Play-distributed build and confirm the Play app-signing certificate/App ID allow-list.
- Use a dedicated device/profile: internal uses the production package and updates the installed public app rather than installing side by side.

## Required license-tester scenarios

- ProductDetails/localized price for every approved INAPP product.
- Purchase success, cancellation, pending payment, repeated purchase after consumption, duplicate callback, and Billing reconnect.
- Backend verification outage and consumption outage with same-token recovery and no duplicate grant.
- App/process restart, reinstall with the same recoverable identity, and outstanding-purchase recovery.
- Two successful free conversions, failed free conversion release, conversion idempotency, multiple expiry buckets, exhaustion/expiry, and ad transitions.
- Existing weekly/monthly subscriber restore, unlimited/ad-free entitlement, cancellation through paid period, and backend-confirmed expiry/revocation.
- No ads during Billing, verification, conversion, document creation, or export critical sections.

No AAB upload, production purchase, deployment, or Play Console mutation is authorized by this checklist.
