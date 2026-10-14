
from pathlib import Path

# Replace the original Android launcher.
launcher_source = Path("app/LauncherActivity.java").read_text(
    encoding="utf-8"
)
launcher_target = Path(
    "upstream/engine/android/app/src/main/java/"
    "org/openbor/engine/LauncherActivity.java"
)
launcher_target.write_text(launcher_source, encoding="utf-8")

# Patch native touch controls.
video = Path("upstream/engine/android/app/jni/openbor/video.c")
source = video.read_text(encoding="utf-8")

needle = """static void setup_touch()
{
    int i;
    setup_touch_default();
    setup_touch_txt();

    for(i = 0; i < MAXTOUCHB; i++)
    {
        btndes[i].x = bx[i] - br[i];"""

replacement = """static int touch_scale_percent = 100;
static int touch_opacity_percent = 65;

static void load_touch_settings(void)
{
    char filename[MAX_FILENAME_LEN];
    FILE *file;
    int scale = 100;
    int opacity = 65;

    snprintf(filename, sizeof(filename),
             "%s/touch_settings.txt", savesDir);

    file = fopen(filename, "r");
    if (file)
    {
        if (fscanf(file, "%d%d", &scale, &opacity) != 2)
        {
            scale = 100;
            opacity = 65;
        }
        fclose(file);
    }

    if (scale < 50) scale = 50;
    if (scale > 150) scale = 150;
    if (opacity < 20) opacity = 20;
    if (opacity > 100) opacity = 100;

    touch_scale_percent = scale;
    touch_opacity_percent = opacity;
}

static void setup_touch()
{
    int i;
    setup_touch_default();
    setup_touch_txt();
    load_touch_settings();

    for(i = 0; i < MAXTOUCHB; i++)
    {
        br[i] *= touch_scale_percent / 100.0f;
        btndes[i].x = bx[i] - br[i];"""

if needle not in source:
    raise SystemExit(
        "ERROR: setup_touch() pattern not found; "
        "upstream source may have changed."
    )

source = source.replace(needle, replacement, 1)

needle = "SDL_SetTextureAlphaMod(buttons, touchstates[i] ? 128 : 64);"
replacement = (
    "SDL_SetTextureAlphaMod(buttons, touchstates[i] "
    "? (128 * touch_opacity_percent / 100) "
    ": (64 * touch_opacity_percent / 100));"
)

if needle not in source:
    raise SystemExit(
        "ERROR: touch opacity pattern not found."
    )

source = source.replace(needle, replacement, 1)
video.write_text(source, encoding="utf-8")

print("Base64E patch applied.")
