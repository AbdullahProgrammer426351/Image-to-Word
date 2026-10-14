# V2 Credits Freeze

Credits development is paused as of 2026-10-14.

- All five V2 backend functions are deployed, but production conversion and subscription-transition behavior remain disabled.
- The Android V2 credit implementation, tests, configuration, and documentation remain preserved on `feature/backend-account-integration`.
- The public release remains based on the original non-credits implementation on `main`.
- Do not merge the credits implementation into `main` during this freeze.
- Resume future credits work by switching back to `feature/backend-account-integration`.
- Future work includes real Play Billing testing, RTDN verification, Play Integrity/App Check validation, and subscription-policy review.

This checkpoint is a preservation point only; it does not change free-conversion behavior, backend settings, Firebase, Play products, or production rollout state.
