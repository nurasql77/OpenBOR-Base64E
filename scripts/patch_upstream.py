from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(".")
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)

JAVA_DIR = ROOT / "upstream/engine/android/app/src/main/java/org/openbor/engine"
for name in ("LauncherActivity.java", "GameActivity.java"):
    source = ROOT / "app" / name
    target = JAVA_DIR / name
    if not source.is_file():
        raise SystemExit(f"Missing overlay source: {source}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")

# Make the custom minimalist launcher the Android entry point.
manifest_path = ROOT / "upstream/engine/android/app/src/main/AndroidManifest.xml"
tree = ET.parse(manifest_path)
manifest = tree.getroot()
application = manifest.find("application")
if application is None:
    raise SystemExit("AndroidManifest.xml has no application element")
name_attr = f"{{{ANDROID_NS}}}name"
launcher = None
for activity in application.findall("activity"):
    if activity.get(name_attr, "") == "org.openbor.engine.LauncherActivity":
        launcher = activity
    for intent in list(activity.findall("intent-filter")):
        actions = {x.get(name_attr, "") for x in intent.findall("action")}
        categories = {x.get(name_attr, "") for x in intent.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            activity.remove(intent)
if launcher is None:
    launcher = ET.SubElement(application, "activity")
    launcher.set(name_attr, "org.openbor.engine.LauncherActivity")
launcher.set(f"{{{ANDROID_NS}}}exported", "true")
launcher.set(f"{{{ANDROID_NS}}}screenOrientation", "landscape")
launcher.set(f"{{{ANDROID_NS}}}theme", "@android:style/Theme.Material.NoActionBar")
intent = ET.SubElement(launcher, "intent-filter")
ET.SubElement(intent, "action").set(name_attr, "android.intent.action.MAIN")
ET.SubElement(intent, "category").set(name_attr, "android.intent.category.LAUNCHER")
tree.write(manifest_path, encoding="utf-8", xml_declaration=True)

# Add live size, opacity, and group-position settings to OpenBOR's native touch UI.
video_path = ROOT / "upstream/engine/android/app/jni/openbor/video.c"
source = video_path.read_text(encoding="utf-8")
needle = """static void setup_touch()
{
    int i;
    setup_touch_default();
    setup_touch_txt();

    for(i = 0; i < MAXTOUCHB; i++)
    {
        btndes[i].x = bx[i] - br[i];
        btndes[i].y = by[i] - br[i];
        btndes[i].h = btndes[i].w = 2 * br[i];
    }
}"""
replacement = r'''
static float touch_base_x[MAXTOUCHB], touch_base_y[MAXTOUCHB], touch_base_r[MAXTOUCHB];
static int touch_scale_percent = 100, touch_opacity_percent = 65;
static int touch_dpad_x = 0, touch_dpad_y = 0, touch_action_x = 0, touch_action_y = 0;

static int load_touch_settings(void)
{
    char filename[MAX_FILENAME_LEN];
    FILE *file;
    int scale = 100, opacity = 65, dx = 0, dy = 0, ax = 0, ay = 0;
    int changed;

    snprintf(filename, sizeof(filename), "%s/touch_settings.txt", savesDir);
    file = fopen(filename, "r");
    if (file)
    {
        fscanf(file, "%d%d%d%d%d%d", &scale, &opacity, &dx, &dy, &ax, &ay);
        fclose(file);
    }
    if (scale < 50) scale = 50;
    if (scale > 150) scale = 150;
    if (opacity < 20) opacity = 20;
    if (opacity > 100) opacity = 100;
    if (dx < -25) dx = -25;
    if (dx > 25) dx = 25;
    if (dy < -25) dy = -25;
    if (dy > 25) dy = 25;
    if (ax < -25) ax = -25;
    if (ax > 25) ax = 25;
    if (ay < -25) ay = -25;
    if (ay > 25) ay = 25;

    changed = scale != touch_scale_percent || opacity != touch_opacity_percent ||
        dx != touch_dpad_x || dy != touch_dpad_y ||
        ax != touch_action_x || ay != touch_action_y;
    touch_scale_percent = scale;
    touch_opacity_percent = opacity;
    touch_dpad_x = dx; touch_dpad_y = dy;
    touch_action_x = ax; touch_action_y = ay;
    return changed;
}

static void apply_touch_settings(void)
{
    int i;
    for (i = 0; i < MAXTOUCHB; i++)
    {
        float dx = 0, dy = 0;
        if (i == SDID_MOVEUP || i == SDID_MOVERIGHT || i == SDID_MOVEDOWN ||
            i == SDID_MOVELEFT || i == SDID_START)
        {
            dx = nativeWidth * touch_dpad_x / 100.0f;
            dy = nativeHeight * touch_dpad_y / 100.0f;
        }
        else if (i == SDID_SPECIAL || i == SDID_ATTACK2 || i == SDID_JUMP ||
                 i == SDID_ATTACK || i == SDID_ATTACK3 || i == SDID_ATTACK4 ||
                 i == SDID_ESC)
        {
            dx = nativeWidth * touch_action_x / 100.0f;
            dy = nativeHeight * touch_action_y / 100.0f;
        }
        bx[i] = touch_base_x[i] + dx;
        by[i] = touch_base_y[i] + dy;
        br[i] = touch_base_r[i] * touch_scale_percent / 100.0f;
        btndes[i].x = (int)(bx[i] - br[i]);
        btndes[i].y = (int)(by[i] - br[i]);
        btndes[i].w = btndes[i].h = (int)(2 * br[i]);
    }
}

static void setup_touch()
{
    int i;
    setup_touch_default();
    setup_touch_txt();
    for (i = 0; i < MAXTOUCHB; i++)
    {
        touch_base_x[i] = bx[i];
        touch_base_y[i] = by[i];
        touch_base_r[i] = br[i];
    }
    load_touch_settings();
    apply_touch_settings();
}'''
if needle not in source:
    raise SystemExit("Could not find native setup_touch() block")
source = source.replace(needle, replacement, 1)
alpha = "SDL_SetTextureAlphaMod(buttons, touchstates[i] ? 128 : 64);"
alpha_new = "SDL_SetTextureAlphaMod(buttons, touchstates[i] ? (128 * touch_opacity_percent / 100) : (64 * touch_opacity_percent / 100));"
if alpha not in source:
    raise SystemExit("Could not find native touch opacity line")
source = source.replace(alpha, alpha_new, 1)
blit_marker = "    hide_touch = (timer_gettick() > hide_t);"
refresh = """    static int touch_refresh = 0;
    if (++touch_refresh >= 15)
    {
        touch_refresh = 0;
        if (load_touch_settings()) apply_touch_settings();
    }

"""
if blit_marker not in source:
    raise SystemExit("Could not find native blit touch-render marker")
source = source.replace(blit_marker, refresh + blit_marker, 1)
video_path.write_text(source, encoding="utf-8")

# Confirm no game data is embedded and native touch controls remain adjustable.
if "installEditorButton();" not in (JAVA_DIR / "GameActivity.java").read_text(encoding="utf-8"):
    raise SystemExit("In-game touch editor was not installed")
if "Intent.ACTION_OPEN_DOCUMENT" not in (JAVA_DIR / "LauncherActivity.java").read_text(encoding="utf-8"):
    raise SystemExit("Game file picker was not installed")
if "base64e-game.pak" in source:
    raise SystemExit("Unexpected bundled game data reference in native video code")
print("Native OpenBOR overlay applied: minimalist launcher, .pak picker, in-game touch editor.")
