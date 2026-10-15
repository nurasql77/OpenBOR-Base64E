# OpenBOR Base64E for Android

A compact Android overlay built on the original [DCurrent/OpenBOR source](https://github.com/DCurrent/openbor).

## Design

The workflow checks out the original OpenBOR repository and builds its native engine and SDL Android application. Base64E does **not** replace the engine or overwrite the original `GameActivity.java`.

Base64E adds:
- a Russian / Kazakh / English launcher;
- selection and copying of a `.pak` game file;
- passing the selected PAK path to the original native OpenBOR entry point;
- a touch-control editor for button size, opacity and control-group position.

## Build

Open **Actions → Build Base64E OpenBOR APK**, then download `OpenBOR-Base64E-Debug` from a successful run. A successful build only confirms compilation; the APK still needs to be tested on a real device.

## Use

1. Install the generated APK.
2. Select a valid OpenBOR `.pak` file in the launcher.
3. Press **Start game**.
4. Game data is stored in the app-specific external files folder, normally `Android/data/kz.base64e.openbor1/files/`, with `Paks`, `Saves`, `Logs`, and `ScreenShots` subfolders.
5. In-game, tap the gear icon to open touch settings.

Game data is not bundled into the APK.

## Upstream

Original engine source and licensing: https://github.com/DCurrent/openbor
