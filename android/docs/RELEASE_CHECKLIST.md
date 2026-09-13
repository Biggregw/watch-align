# Release checklist

- [ ] Run unit tests and assemble the arm64 debug APK.
- [ ] Build the release AAB with `gradle :app:bundleRelease`.
- [ ] Provide `WATCH_ALIGN_KEYSTORE`, `WATCH_ALIGN_STORE_PASSWORD`, `WATCH_ALIGN_KEY_ALIAS` and `WATCH_ALIGN_KEY_PASSWORD` for a signed release.
- [ ] Confirm release is non-debuggable and contains no INTERNET permission.
- [ ] Exercise gallery, guided camera, manual four-point alignment, triangle point correction, final QC, history reopen and share export on a physical arm64 device.
- [ ] Compare known genuine-control results with the Alpha52 regression tests.
- [ ] Verify adaptive, round and monochrome launcher icons.
- [ ] Review `PRIVACY.md` against the final manifest and dependencies.
- [ ] Reconfirm the target SDK and Play policy immediately before submission.
- [ ] Upload the signed AAB to an internal Play test track before production.
