"""Read UI state and exercise line controls on an installed signed Release.

Keeps sources, favorites and app data intact. Writes assertions and raw hierarchy
snapshots to the ignored build output directory. Does not change device settings.
"""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "cn.hdfk7.android.iptv"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    output = Path("app/build/outputs/qa/line-ranking")
    output.mkdir(parents=True, exist_ok=True)

    def adb(*arguments):
        return subprocess.run(["adb", "-s", args.serial, *arguments], check=True,
                              capture_output=True, text=True, timeout=20).stdout

    metadata = json.loads(Path("app/build/outputs/apk/release/output-metadata.json").read_text())
    package = adb("shell", "dumpsys", "package", PACKAGE)
    expected = metadata["elements"][0]
    assert "versionCode=" + str(expected["versionCode"]) + " " in package
    assert "DEBUGGABLE" not in package
    records = []

    def bounds(node):
        return list(map(int, re.findall(r"\d+", node.get("bounds", ""))))

    def button(root, description):
        parents = {child: parent for parent in root.iter() for child in parent}
        node = next(node for node in root.iter("node") if node.get("content-desc") == description)
        while node.get("clickable") != "true":
            node = parents[node]
        return node

    def tap(node):
        left, top, right, bottom = bounds(node)
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def snapshot(name):
        adb("shell", "input", "keyevent", "KEYCODE_MENU")
        time.sleep(.25)
        adb("shell", "uiautomator", "dump", "/sdcard/iptv-line-ranking.xml")
        xml = adb("shell", "cat", "/sdcard/iptv-line-ranking.xml")
        (output / (name + ".xml")).write_text(xml)
        root = ET.fromstring(xml)
        previous = button(root, "上一条线路")
        following = button(root, "下一条线路")
        number = lambda item: int(next(node.get("text") for node in item.iter("node")
                                      if node.get("text", "").isdigit()))
        program = next(node.get("text") for node in root.iter("node")
                       if node.get("class") == "android.widget.TextView" and node.get("text")
                       and bounds(node)[0] < 700 and bounds(node)[1] > 800)
        resolution = next((node.get("text") for node in previous.iter("node")
                           if re.fullmatch(r"\d+p", node.get("text", ""))), None)
        state = {"step": name, "program": program, "line": number(previous),
                 "count": number(following), "resolution": resolution}
        assert 1 <= state["line"] <= state["count"], state
        records.append(state)
        return root, state

    try:
        root, initial = snapshot("initial")
        assert initial["count"] > 1, "The selected program needs multiple lines"
        for direction, description in [(1, "下一条线路"), (-1, "上一条线路")]:
            before = records[-1]
            tap(button(root, description))
            # Freeze immediately so live quality reordering cannot race the
            # assertion for a single manual index transition.
            pause = next((node for node in root.iter("node") if node.get("content-desc") == "暂停"), None)
            if pause is not None:
                tap(button(root, "暂停"))
            root, after = snapshot("next" if direction > 0 else "previous")
            assert after["program"] == before["program"], (before, after)
            assert after["count"] == before["count"], (before, after)
            assert after["line"] == (before["line"] - 1 + direction) % before["count"] + 1, (before, after)
        adb("shell", "input", "keyevent", "KEYCODE_CHANNEL_UP")
        root, switched = snapshot("program_changed")
        assert switched["program"] != initial["program"], (initial, switched)
        time.sleep(6)
        root, observed = snapshot("after_observation")
        assert observed["program"] == switched["program"], (switched, observed)
        assert observed["count"] == switched["count"], (switched, observed)
        adb("shell", "input", "keyevent", "KEYCODE_CHANNEL_DOWN")
        root, returned = snapshot("program_returned")
        assert returned["program"] == initial["program"], (initial, returned)
        report = {"passed": True, "version": expected["versionName"], "steps": records}
    except Exception as error:
        report = {"passed": False, "error": str(error), "steps": records}
        raise
    finally:
        (output / "results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        # Leave playback running after the test.
        adb("shell", "input", "keyevent", "KEYCODE_MEDIA_PLAY")
        print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
