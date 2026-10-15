# OpenBOR-Base64E

A native Android build of the original [DCurrent/OpenBOR](https://github.com/DCurrent/openbor) engine with a minimalist launcher and an in-game touch-control editor.

## Game files and data

Game `.pak` files are selected on the device and copied to the app-specific external storage folder. They are **not bundled into the APK**.

The original OpenBOR storage layout is preserved:

- `files/Logs` — engine logs
- `files/Paks` — game `.pak` files
- `files/Saves` — saves and touch-control settings
- `files/ScreenShots` — screenshots (upstream spelling)

On Android this is the app-specific external-files directory, normally shown as:

`Android/data/kz.base64e.openbor1/files/`

The exact package folder can differ for builds using a different application ID.

## Launcher and controls

- Russian, Kazakh, and English launcher labels.
- Select a `.pak` file, then press Start to launch the native engine.
- Existing `.pak` files are kept when another game file is added.
- The in-game gear button opens touch settings for size, opacity, and X/Y positioning of the D-pad and action-button groups.

The build workflow checks the upstream storage directory names before applying the overlay.
