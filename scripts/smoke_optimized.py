"""Launch and scroll the optimized APK on the CI emulator; retain frame diagnostics.
Frame counts are diagnostics, not a phone-performance guarantee or emulator FPS gate.
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
    adb("shell", "uiautomator", "dump", "/sdcard/qubat-scroll.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/qubat-scroll.xml"))


adb("install", "-r", "app/build/outputs/apk/release/app-release.apk")
adb("logcat", "-c")
adb("shell", "am", "force-stop", PKG)
adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity")
time.sleep(2.5)  # Let the finite launch animation finish before touch input.
for attempt in range(12):
    root = tree()
    node = next((n for n in root.iter("node") if n.get("text") == "Список"), None)
    if node is not None:
        break
    time.sleep(.5)
else:
    raise AssertionError("Optimized APK did not display its navigation")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
root = tree()
if not any("DEMO" in n.get("text", "") for n in root.iter("node")):
    raise AssertionError("Station list did not open in the optimized APK")
sizes = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
w, h = map(int, sizes[-1])
adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
for start, end in ((.78, .28), (.28, .78)):
    for _ in range(6):
        adb("shell", "input", "swipe", str(w // 2), str(int(h * start)), str(w // 2), str(int(h * end)), "180")
root = tree()
if not any(n.get("text") == "Список" for n in root.iter("node")):
    raise AssertionError("List navigation disappeared after rapid scrolling")
frames = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
(OUT / "frames.txt").write_text(frames)
(OUT / "screen.xml").write_text(ET.tostring(root, encoding="unicode"))
(OUT / "screen.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
logs = adb("logcat", "-d", "-s", "AndroidRuntime:E")
(OUT / "runtime.txt").write_text(logs)
if "FATAL EXCEPTION" in logs and PKG in logs:
    raise AssertionError("Optimized APK crashed during the scroll check")
print("Optimized APK opened and completed 12 rapid swipes.")
for line in frames.splitlines():
    if any(key in line for key in ("Total frames rendered", "Janky frames", "50th percentile", "90th percentile")):
        print(line.strip())
