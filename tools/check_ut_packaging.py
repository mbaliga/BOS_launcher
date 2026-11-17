#!/usr/bin/env python3
"""Static checks for the Ubuntu Touch Click sources (no Clickable, no device needed).
Run: python3 tools/check_ut_packaging.py   -> exit 1 on any failure."""
import json, os, re, sys, configparser

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
UT = os.path.join(ROOT, "ubuntu-touch")
# Policy groups that exist in the common Lomiri set and that this project may request (F7: common only).
ALLOWED = {"url_dispatcher", "webview", "networking", "audio", "content_exchange"}
errors = []

def err(m): errors.append(m)

def check(d, hook, network_ok):
    base = os.path.join(UT, d)
    man = json.load(open(os.path.join(base, "manifest.json.in")))
    for k in ("name", "title", "version", "maintainer", "framework", "architecture", "hooks"):
        if k not in man: err(f"{d}: manifest missing {k}")
    if man.get("architecture") != "@CLICK_ARCH@": err(f"{d}: architecture must be @CLICK_ARCH@")
    if man.get("framework") != "@CLICK_FRAMEWORK@": err(f"{d}: framework must be @CLICK_FRAMEWORK@")
    if "Liftoff" not in man.get("title", ""): err(f"{d}: title should say Liftoff")
    h = man["hooks"].get(hook, {})
    for kind in ("apparmor", "desktop"):
        f = h.get(kind)
        if not f or not os.path.isfile(os.path.join(base, f)): err(f"{d}: hook {kind} file {f!r} missing")
    aa = json.load(open(os.path.join(base, h["apparmor"])))
    if aa.get("template") not in (None, "ubuntu-sdk"): err(f"{d}: unconfined/custom template not allowed")
    groups = set(aa.get("policy_groups", []))
    if not groups <= ALLOWED: err(f"{d}: non-common policy groups {groups - ALLOWED}")
    if not network_ok and "networking" in groups: err(f"{d}: networking not allowed (no-phoning-home rule)")
    cp = configparser.ConfigParser(interpolation=None, strict=True); cp.optionxform = str
    cp.read(os.path.join(base, h["desktop"]))
    de = cp["Desktop Entry"]
    for k in ("Name", "Exec", "Icon", "Type"):
        if k not in de: err(f"{d}: desktop missing {k}")
    if de.get("Terminal") != "false": err(f"{d}: Terminal must be false")
    cm = open(os.path.join(base, "CMakeLists.txt")).read()
    for needle in ("manifest.json.in RENAME manifest.json", h["apparmor"], h["desktop"]):
        if needle not in cm: err(f"{d}: CMakeLists does not install {needle}")
    cl = open(os.path.join(base, "clickable.yaml")).read()
    if "builder: pure-qml-cmake" not in cl: err(f"{d}: clickable.yaml builder")
    return de

pd = check("picker", "liftoff", network_ok=False)
if "qml/Main.qml" not in pd["Exec"] or "QML_XHR_ALLOW_FILE_READ=1" not in pd["Exec"]: err("picker: Exec must run qml/Main.qml with QML_XHR_ALLOW_FILE_READ=1")
dd = check("demo", "liftoff-demo", network_ok=True)
if "www/index.html" not in dd["Exec"]: err("demo: Exec must open www/index.html")
# The prototype shipped in the demo must make no network requests.
html = open(os.path.join(ROOT, "docs", "spherelauncher13.html"), encoding="utf8").read()
if re.search(r"(src|href)=[\"']https?://|fetch\(|XMLHttpRequest|WebSocket", html): err("demo: prototype references the network")
# QML sources must not import Lomiri or network modules (keeps the picker testable off-device and offline).
for f in os.listdir(os.path.join(UT, "picker", "qml")):
    if f.endswith(".qml"):
        t = open(os.path.join(UT, "picker", "qml", f)).read()
        if re.search(r"import\s+(Lomiri|Ubuntu)\.|QtWebEngine|QtNetwork", t): err(f"picker/qml/{f}: forbidden import")
if errors:
    print("\n".join("FAIL " + e for e in errors)); sys.exit(1)
print("ubuntu-touch packaging checks: OK")
