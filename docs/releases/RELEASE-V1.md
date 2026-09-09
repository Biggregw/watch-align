Watch Align V1 1.0.0 adds model-aware QC Analysis and Gen Compare for 126710BLNR and 124060.

Low overall, region or individual feature confidence now withholds precise marker, bezel and date measurements in the V1 UI, JSON and report. Severe off-axis capture or perspective mismatch also blocks Gen Compare measurements. A prominent warning explains the limitation. Perspective estimate confidence describes certainty in the estimate, not good camera geometry.

The Windows launcher opens http://127.0.0.1:8001/v1 and retains a separate lock from legacy builds. Use main_v1:app for server launches. The production installer and portable ZIP come from the same tested Windows build. Keep the entire portable folder together.

Rollback: previous release assets and tags are retained. Quit V1 before reinstalling the previous installer from GitHub Releases, or run the previous portable release. Existing reference data should be backed up before changing versions; this release does not migrate it. The pre-V1 main commit is d819252d00b07c0be2abc5b4ad41f3d4a7d43220. Revert the V1 merge to restore that code; do not reset shared branch history or overwrite release tags. Release publication refuses an existing v1.0.0 tag and uploads a draft before changing latest.

Measurements remain image geometry estimates, not physical metrology or proof of authenticity. Thresholds have synthetic regression coverage; real-world calibration remains limited. No built-in photographs are claimed as verified genuine.
