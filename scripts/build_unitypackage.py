#!/usr/bin/env python3
"""Build a deterministic Unity .unitypackage from the public bridge + Android AAR."""

from pathlib import Path
import gzip
import io
import tarfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dist/unity/PocketsFullMonetize-Unity-1.0.0.unitypackage"
FIXED_MTIME = 0

ASSETS = [
    {
        "guid": "287ec2362fc74b8fb84825b81390fdc2",
        "path": "Assets/Plugins/Android/PocketsFullMonetize-1.0.0.aar",
        "source": ROOT / "dist/android/PocketsFullMonetize-1.0.0.aar",
        "meta": """fileFormatVersion: 2
guid: 287ec2362fc74b8fb84825b81390fdc2
PluginImporter:
  externalObjects: {}
  serializedVersion: 2
  iconMap: {}
  executionOrder: {}
  defineConstraints: []
  isPreloaded: 0
  isOverridable: 0
  isExplicitlyReferenced: 0
  validateReferences: 1
  platformData:
  - first:
      Any:
    second:
      enabled: 0
      settings: {}
  - first:
      Android: Android
    second:
      enabled: 1
      settings:
        CPU: AnyCPU
  userData:
  assetBundleName:
  assetBundleVariant:
""",
    },
    {
        "guid": "6db5723f8b3b4b10b15d7504383d86a1",
        "path": "Assets/PocketsFullMonetize/PocketsFullUnity.cs",
        "source": ROOT / "unity/Assets/PocketsFullMonetize/PocketsFullUnity.cs",
        "meta": "fileFormatVersion: 2\nguid: 6db5723f8b3b4b10b15d7504383d86a1\n",
    },
    {
        "guid": "b25f3f5268384b30acb7aeb4a7b17112",
        "path": "Assets/PocketsFullMonetize/ExampleExtraLife.cs",
        "source": ROOT / "unity/Assets/PocketsFullMonetize/ExampleExtraLife.cs",
        "meta": "fileFormatVersion: 2\nguid: b25f3f5268384b30acb7aeb4a7b17112\n",
    },
]


def add_bytes(tar: tarfile.TarFile, name: str, data: bytes) -> None:
    info = tarfile.TarInfo(name)
    info.size = len(data)
    info.mtime = FIXED_MTIME
    info.mode = 0o644
    tar.addfile(info, io.BytesIO(data))


def main() -> None:
    for asset in ASSETS:
        if not asset["source"].exists():
            raise SystemExit(f"Missing source file: {asset['source']}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    tar_buffer = io.BytesIO()
    with tarfile.open(fileobj=tar_buffer, mode="w", format=tarfile.GNU_FORMAT) as tar:
        for asset in ASSETS:
            guid = asset["guid"]
            add_bytes(tar, f"{guid}/pathname", asset["path"].encode("utf-8"))
            add_bytes(tar, f"{guid}/asset", asset["source"].read_bytes())
            add_bytes(tar, f"{guid}/asset.meta", asset["meta"].encode("utf-8"))

    with OUT.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=FIXED_MTIME) as gz:
            gz.write(tar_buffer.getvalue())

    print(f"Built {OUT.relative_to(ROOT)} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
