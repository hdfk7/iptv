"""Restore this QA run's original app data and emulator network/display settings."""

import argparse
import json
import os
from pathlib import Path

from android_tv_suite import TvSuite


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5580")
    parser.add_argument("--backup", type=Path, default=Path(os.environ["TEMP"]) / "iptv-qa-original-data.tar")
    parser.add_argument("--output", type=Path, default=Path("app/build/outputs/qa"))
    arguments = parser.parse_args()
    if not arguments.serial.startswith("emulator-"):
        raise RuntimeError("This recovery script is only for a disposable emulator")
    if not arguments.backup.is_file():
        raise FileNotFoundError("Original application backup is missing")
    suite = TvSuite(arguments.output, arguments.serial)
    suite.network(True)
    suite.run("shell", "wm", "size", "reset")
    suite.run("shell", "wm", "density", "reset")
    suite.restore(arguments.backup)
    suite.launch()
    library = suite.library()
    result = {
        "restored": True,
        "sourceCount": len(library["sources"]),
        "activeSource": library["activeSourceId"],
        "favoriteContributions": len(library["favorites"]),
        "networkRestored": True,
        "displayRestored": True,
    }
    (arguments.output / "restoration.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))
