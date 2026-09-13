# Physical-device capture test

Connect an arm64 Android device with a rear Camera2 JPEG stream, unlock it, and run:

```sh
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.watchalign.mobile.CaptureHistoryExportDeviceTest
```

The test verifies that the device exposes an appropriate JPEG still size and exercises the same captured-bitmap handoff, private history persistence, reopen and secure export-file path used by the app. CI compiles this instrumentation APK on every change. Camera framing, focus, EXIF orientation and preview crop must additionally be checked in portrait and landscape on at least one Samsung device before Play submission because hosted CI has no physical camera sensor.
