"""Exercise every screen of the optimized APK and retain frame diagnostics/previews.
Emulator frame counts are diagnostics, not a phone FPS guarantee or a timing gate.
"""
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "kg.qubat.ev"
OUT = pathlib.Path("app/build/reports/androidTests/optimized-scroll")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, binary=False):
    return subprocess.check_output(["adb", *args], timeout=45, text=not binary)


def tree():
    for attempt in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/qubat-scroll.xml")
            return ET.fromstring(adb("exec-out", "cat", "/sdcard/qubat-scroll.xml"))
        except (subprocess.CalledProcessError, ET.ParseError):
            if attempt == 2:
                raise
            time.sleep(.5)


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))


def tap(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def navigation(label):
    nodes = [n for n in tree().iter("node") if n.get("text") == label]
    if not nodes:
        raise AssertionError("Navigation missing: " + label)
    tap(max(nodes, key=lambda n: bounds(n)[1]))


def capture(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))


adb("install", "-r", "app/build/outputs/apk/release/app-release.apk")
adb("logcat", "-c")
adb("shell", "am", "force-stop", PKG)
adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity")
time.sleep(2.5)
sizes = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
w, h = map(int, sizes[-1])
# Verify the new native cabinet in the optimized APK, including persisted debug-to-release data.
def find_label(label, exact=False, scroll=True):
    for attempt in range(30 if scroll else 1):
        matches = []
        for node in tree().iter("node"):
            value = " ".join((node.get("text", "") or node.get("content-desc", "")).split())
            x1, y1, x2, y2 = bounds(node)
            if (value == label if exact else label in value) and x2 > x1 and y2 > y1 and 0 <= y1 < h:
                matches.append(node)
        if matches:
            return min(matches, key=lambda n: len(n.get("text", "")))
        if scroll:
            # Short, slow drags keep small titles visible between hierarchy snapshots;
            # a fast fling can jump past the first card below the tall filter panel.
            adb("shell", "input", "swipe", str(w // 2), str(int(h * .68)), str(w // 2), str(int(h * .48)), "550")
    print(ET.tostring(tree(), encoding="unicode"))
    raise AssertionError("Optimized native partner label missing: " + label)

navigation("Профиль")
tap(find_label("О приложении", exact=True))
title = find_label("QUBAT EV", exact=True, scroll=False)
x1, y1, x2, y2 = bounds(title)
adb("shell", "input", "swipe", str((x1+x2)//2), str((y1+y2)//2), str((x1+x2)//2), str((y1+y2)//2), "900")
tap(find_label("Кубат Север", scroll=False))
find_label("Мониторинг", exact=True, scroll=False)
menu = [n for n in tree().iter("node") if n.get("content-desc") == "Меню кабинета"]
if not menu:
    raise AssertionError("Optimized native partner menu missing")
tap(menu[0])
tap(find_label("Точки", exact=True, scroll=False))
tap(find_label("QUBAT 1 · ДЕМО"))
tap(find_label("Параметры", exact=True))
find_label("14,75")  # UI test saved a future tariff: serialization must survive R8 and app restart.
capture("native-partner-release-persistence")
print("Optimized native partner cabinet opened; persisted tariff 14.75 survived app restart and release optimization.")

back = [n for n in tree().iter("node") if n.get("content-desc") == "Назад в приложение"]
assert back, "Native cabinet return missing"
tap(back[0])
tree()

for label, name in (("Список", "list"), ("Зарядка", "charging"), ("Кошелёк", "wallet"), ("Профиль", "profile"), ("Карта", "map")):
    navigation(label)
    tree()  # Wait for the destination to settle before resetting frame counters.
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    for start, end in ((.78, .28), (.28, .78)):
        for _ in range(6):
            adb("shell", "input", "swipe", str(w // 2), str(int(h * start)), str(w // 2), str(int(h * end)), "180")
    root = tree()
    if not any(n.get("text") == label for n in root.iter("node")):
        raise AssertionError("Navigation disappeared on " + name)
    frames = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
    (OUT / (name + "-frames.txt")).write_text(frames)
    (OUT / (name + "-screen.xml")).write_text(ET.tostring(root, encoding="unicode"))
    capture(name)
    print("Optimized screen " + name + ": completed 12 swipes.")
    seen = set()
    for line in frames.splitlines():
        value = line.strip()
        if value not in seen and any(key in value for key in ("Total frames rendered", "Janky frames:", "50th percentile", "90th percentile")):
            print(name + ": " + value)
            seen.add(value)
# Retain a second-theme preview for the main framed screens.
for label, name in (("Кошелёк", "wallet"), ("Профиль", "profile")):
    navigation(label)
    for _ in range(5):
        adb("shell", "input", "swipe", str(w // 2), str(int(h * .3)), str(w // 2), str(int(h * .8)), "160")
    nodes = [n for n in tree().iter("node") if n.get("content-desc") in ("Дневная тема", "Ночная тема")]
    if not nodes:
        raise AssertionError("Theme toggle missing on " + name)
    tap(nodes[0])
    tree()
    capture(name + "-alternate-theme")
    nodes = [n for n in tree().iter("node") if n.get("content-desc") in ("Дневная тема", "Ночная тема")]
    tap(nodes[0])

logs = adb("logcat", "-d", "-s", "AndroidRuntime:E")
(OUT / "runtime.txt").write_text(logs)
if "FATAL EXCEPTION" in logs and PKG in logs:
    raise AssertionError("Optimized APK crashed during the all-screen check")
print("Optimized APK completed all 5 screens, 60 swipes and both-theme previews.")
