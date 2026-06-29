; Watch Align - Inno Setup installer script.
; Builds WatchAlignSetup.exe, a normal Windows installer wizard for the app.
;
; You need Inno Setup installed once to build this (free, from
; https://jrsoftware.org/isdl.php). After that, build.bat runs this
; automatically every time - you don't run Inno Setup by hand.

#define MyAppName "Watch Align"
#define MyAppVersion "0.9.4"
#define MyAppPublisher "Watch Align"
#define MyAppExeName "WatchAlign.exe"

[Setup]
; A fixed GUID identifies this app to Windows across versions so upgrades/
; uninstalls work correctly. Generated once for this project - don't reuse
; this for a different app.
AppId={{8F2C1A4E-6B3D-4A9F-9E1C-2D5B7A8F3C61}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
; Installs to the current user's AppData by default, which means no admin
; rights are required to install. Good fit for a personal/tester tool.
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
PrivilegesRequired=lowest
OutputDir=installer_output
OutputBaseFilename=WatchAlignSetup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
; Standard uninstaller entry so the app shows up in Settings > Apps.
UninstallDisplayIcon={app}\{#MyAppExeName}
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &Desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
; The app is now an onedir PyInstaller build: dist\WatchAlign\ contains
; WatchAlign.exe plus all its support DLLs and data files. We pull in the
; whole folder recursively. "recursesubdirs createallsubdirs" ensures
; nested folders (e.g. the bundled libraries) are included too.
Source: "dist\WatchAlign\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; Offer to launch the app immediately after install finishes.
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName} now"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; The app stores uploaded photos/exports in a "runtime" folder next to the
; exe. Remove it on uninstall too so nothing is left behind. If testers
; might want to keep their files, this section can simply be deleted.
Type: filesandordirs; Name: "{app}\runtime"
; At first run the app writes static assets (CSS/JS/HTML) into _internal\static\.
; That folder is not tracked by the installer, so we clean it up explicitly.
Type: filesandordirs; Name: "{app}\_internal\static"
