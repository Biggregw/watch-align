# Privacy and Play Data Safety

Watch Align processes watch photos, camera preview frames, reference photos and geometry measurements on the device. It has no Internet permission, analytics SDK, advertising SDK, account system or cloud backend.

- Camera permission is used only for guided capture.
- Photos chosen by the user are read through Android's document picker.
- Inspection history and confirmed-genuine reference photos are stored in private app storage.
- Nothing is transmitted by the app. A file leaves the app only when the user presses **Share QC** and chooses an Android share target.
- Clearing app data or uninstalling removes private history and references.

Play Console Data Safety should declare no collected or shared data. The share action is user-initiated transfer, and the camera/photo access is core app functionality. Recheck these answers whenever a network, analytics, backup or account feature is introduced.
