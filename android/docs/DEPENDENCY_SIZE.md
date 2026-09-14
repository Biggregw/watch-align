# Dependency and size report

## Before issue #10

- Runtime: OpenCV 4.13.0.
- ABI: arm64-v8a only.
- Duplicate JSON model/template assets and version-stacked analysis cores were present on the Alpha52 line.

## After issue #10

- Runtime: OpenCV 4.13.0 plus AndroidX Core 1.15.0 for secure `FileProvider` sharing.
- ABI: arm64-v8a only.
- No network client or analytics dependency.
- Duplicate runtime geometry representations and obsolete versioned cores removed.
- Release uses R8/resource shrinking and compressed native libraries.

CI records exact APK and AAB byte sizes in the build summary so size changes remain visible per commit.
