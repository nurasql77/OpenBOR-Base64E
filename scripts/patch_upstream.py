from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(".")
ANDROID_NS = "http://schemas.android.com/apk/res/android"

ET.register_namespace("android", ANDROID_NS)

# ------------------------------------------------------------------
# 1. Base64E мәзірін OpenBOR Android жобасына көшіру
# ------------------------------------------------------------------
launcher_source = ROOT / "app/LauncherActivity.java"
launcher_target = (
    ROOT / "upstream/engine/android/app/src/main/java/"
    "org/openbor/engine/LauncherActivity.java"
)

if not launcher_source.is_file():
    raise SystemExit(f"ERROR: Missing launcher: {launcher_source}")

source_text = launcher_source.read_text(encoding="utf-8")

# Preserve the custom language menu and control settings, but only launch
# the bundled game after the user taps the Start button.
if "private void showMenu()" not in source_text:
    raise SystemExit("ERROR: Base64E menu is missing.")
if 'private void startGame()' not in source_text:
    raise SystemExit("ERROR: Start game button handler is missing.")
if 'getAssets().open("base64e-game.pak")' not in source_text:
    raise SystemExit("ERROR: Launcher does not install the bundled Base64E PAK.")
if 'new File(paks, "base64e-game.pak")' not in source_text:
    raise SystemExit("ERROR: Launcher does not copy PAK to OpenBOR's Paks folder.")
if "new Intent(this, GameActivity.class)" not in source_text:
    raise SystemExit("ERROR: Launcher does not start GameActivity.")

launcher_target.parent.mkdir(parents=True, exist_ok=True)
launcher_target.write_text(source_text, encoding="utf-8")

# ------------------------------------------------------------------
# 2. AndroidManifest.xml ішіндегі іске қосу Activity-сін түзету
# ------------------------------------------------------------------
manifest_path = (
    ROOT / "upstream/engine/android/app/src/main/AndroidManifest.xml"
)

if not manifest_path.is_file():
    raise SystemExit("ERROR: AndroidManifest.xml is missing.")

tree = ET.parse(manifest_path)
manifest = tree.getroot()
application = manifest.find("application")

if application is None:
    raise SystemExit("ERROR: <application> element is missing.")

name_attr = f"{{{ANDROID_NS}}}name"
exported_attr = f"{{{ANDROID_NS}}}exported"
theme_attr = f"{{{ANDROID_NS}}}theme"
orientation_attr = f"{{{ANDROID_NS}}}screenOrientation"

launcher_activity = None

# Ескі MAIN/LAUNCHER фильтрлерін алып тастау
for activity in application.findall("activity"):
    activity_name = activity.get(name_attr, "")

    if activity_name == "org.openbor.engine.LauncherActivity":
        launcher_activity = activity

    for intent_filter in list(activity.findall("intent-filter")):
        actions = {
            node.get(name_attr, "")
            for node in intent_filter.findall("action")
        }
        categories = {
            node.get(name_attr, "")
            for node in intent_filter.findall("category")
        }

        if (
            "android.intent.action.MAIN" in actions
            and "android.intent.category.LAUNCHER" in categories
        ):
            activity.remove(intent_filter)

if launcher_activity is None:
    launcher_activity = ET.SubElement(application, "activity")
    launcher_activity.set(name_attr, "org.openbor.engine.LauncherActivity")

launcher_activity.set(exported_attr, "true")
launcher_activity.set(orientation_attr, "landscape")
launcher_activity.set(theme_attr, "@android:style/Theme.Material.NoActionBar")

intent_filter = ET.SubElement(launcher_activity, "intent-filter")

action = ET.SubElement(intent_filter, "action")
action.set(name_attr, "android.intent.action.MAIN")

category = ET.SubElement(intent_filter, "category")
category.set(name_attr, "android.intent.category.LAUNCHER")

tree.write(manifest_path, encoding="utf-8", xml_declaration=True)

# ------------------------------------------------------------------
# 3. Сенсорлық басқару өлшемі мен мөлдірлігін түзету (video.c)
# ------------------------------------------------------------------
video_path = (
    ROOT / "upstream/engine/android/app/jni/openbor/video.c"
)

if not video_path.is_file():
    raise SystemExit("ERROR: video.c is missing.")

source = video_path.read_text(encoding="utf-8")

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
    raise SystemExit("ERROR: setup_touch() pattern not found in video.c.")

source = source.replace(needle, replacement, 1)

needle_alpha = (
    "SDL_SetTextureAlphaMod(buttons, "
    "touchstates[i] ? 128 : 64);"
)

replacement_alpha = (
    "SDL_SetTextureAlphaMod(buttons, touchstates[i] "
    "? (128 * touch_opacity_percent / 100) "
    ": (64 * touch_opacity_percent / 100));"
)

if needle_alpha not in source:
    raise SystemExit("ERROR: Touch opacity pattern not found in video.c.")

source = source.replace(needle_alpha, replacement_alpha, 1)
video_path.write_text(source, encoding="utf-8")

# ------------------------------------------------------------------
# 4. Тексеру
# ------------------------------------------------------------------
installed_launcher = launcher_target.read_text(encoding="utf-8")

if "private void showMenu()" not in installed_launcher:
    raise SystemExit("ERROR: Base64E menu was not installed.")
if 'getAssets().open("base64e-game.pak")' not in installed_launcher:
    raise SystemExit("ERROR: Bundled Base64E PAK installation was not installed.")

check_tree = ET.parse(manifest_path)
check_app = check_tree.getroot().find("application")
launchers = []

for activity in check_app.findall("activity"):
    for intent_filter in activity.findall("intent-filter"):
        actions = {
            node.get(name_attr, "")
            for node in intent_filter.findall("action")
        }
        categories = {
            node.get(name_attr, "")
            for node in intent_filter.findall("category")
        }

        if (
            "android.intent.action.MAIN" in actions
            and "android.intent.category.LAUNCHER" in categories
        ):
            launchers.append(activity.get(name_attr, ""))

expected = ["org.openbor.engine.LauncherActivity"]

if launchers != expected:
    raise SystemExit(f"ERROR: Wrong launcher activity: {launchers}")

print("Base64E patch applied successfully.")
print("Base64E language menu and control settings verified.")
print("LauncherActivity entry point verified.")
print("Start button installs bundled PAK and launches GameActivity.")
print("Touch scale + opacity support added.")