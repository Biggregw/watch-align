# Watch Align

A local Windows tool for comparing two watch photos. You drop in a reference image and a candidate image, and it automatically aligns them — matching dial size, rotation, and position — then lets you inspect the differences using blink, slider, overlay, edge, and heatmap views. There's also an appearance-matching mode that normalises colour and exposure between the two before comparing. Everything runs locally on your PC; nothing is uploaded anywhere.

**Limitation worth knowing:** the alignment works best on roughly straight-on photos. A shot taken at an angle distorts the dial geometry, and the algorithm has no way to correct for perspective — so a badly-angled photo will produce a poor alignment regardless of how well the two watches actually match. Treat it as a useful aid, not a verdict.

---

## Download

**[WatchAlignSetup.exe — latest release](https://github.com/Biggregw/watch-align/releases/latest/download/WatchAlignSetup.exe)**

Double-click the installer, follow the wizard (Next → Next → Install), and it appears in your Start Menu. No Python, no command line, nothing else needed.

### SmartScreen warning

Windows will show "Windows protected your PC" the first time because the app isn't signed. This is expected for a small personal tool. Click **More info → Run anyway** to proceed. If you'd rather not trust a binary from a stranger on the internet, the source is all here and you can build it yourself (see below).

---

## Build from source

Requires Python 3.11+ and Git. Inno Setup is optional — only needed if you want to produce the installer yourself rather than just run the app directly.

```
git clone https://github.com/Biggregw/watch-align.git
cd watch-align
build.bat
```

`build.bat` creates a virtual environment, installs dependencies, and runs PyInstaller. If Inno Setup is installed it also builds `WatchAlignSetup.exe`. The finished app is in `dist\WatchAlign\WatchAlign.exe`.

---

## Notes

- v0.9, Windows only
- Feedback welcome — issues or comments here on GitHub are fine
- If you find it useful: [buymeacoffee.com/biggregw](https://buymeacoffee.com/biggregw)
