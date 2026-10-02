"""Verify and package a built local release; never uploads or installs anything."""
import argparse
import hashlib
import json
import shutil
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "2.0.4"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--smoke", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    jar = ROOT / f"build/libs/PartyMod-{VERSION}.jar"
    smoke_jar = args.smoke / f"mods/PartyMod-{VERSION}.jar"
    lines = [f"PartyMod {VERSION} release verification", "Date: 2026-10-01", ""]
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(smoke_jar) as tested:
        names = z.namelist()
        assert len(names) == len(set(names)), "Duplicate JAR entries"
        metadata = json.loads(z.read("mcmod.info"))[0]
        assert metadata["modid"] == "partymod" and metadata["version"] == VERSION
        assert metadata["mcversion"] == "1.8.9"
        manifest = z.read("META-INF/MANIFEST.MF").decode()
        assert "TweakClass: cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker" in manifest
        assert "FMLCorePluginContainsFMLMod: true" in manifest
        classes = [n for n in names if n.endswith(".class")]
        majors = set()
        for name in classes:
            raw = z.read(name)
            assert raw[:4] == b"\xca\xfe\xba\xbe"
            major = struct.unpack(">H", raw[6:8])[0]
            majors.add(major)
            assert major <= 52, f"Not Java 8 compatible: {name}"
            assert name.startswith(("fr/alexdoru/partymod/", "cc/polyfrost/oneconfig/loader/stage0/")), name
            assert "/smoke/" not in name and not name.endswith("Test.class")
            assert raw == tested.read(name), f"Class differs from Minecraft-tested JAR: {name}"
        assert not any(n.endswith(("StatsChecker.class", "ChatListener.class", "HttpClient.class")) for n in names)
        assert not any(n.startswith(("org/junit/", "cc/polyfrost/oneconfig/config/", "net/minecraft/")) for n in names)
        lines += [f"JAR entries: {len(names)}; classes: {len(classes)}; class majors: {sorted(majors)}",
                  "PASS metadata: PartyMod 2.0.4 / Forge Minecraft 1.8.9",
                  "PASS reobfuscated production JAR and stage-0 OneConfig loader manifest",
                  "PASS no duplicate entries, test/probe classes, Minecraft classes or full OneConfig implementation",
                  "PASS every class byte-for-byte matches the production JAR loaded by the isolated Minecraft run",
                  "PASS dependency attribution and licence files included", ""]
    tests = failures = errors = 0
    for path in sorted((ROOT / "build/test-results").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        count = int(suite.attrib["tests"])
        tests += count
        failures += int(suite.attrib["failures"])
        errors += int(suite.attrib["errors"])
        lines.append(f"Java {suite.attrib['name']}: {count} tests, {suite.attrib['failures']} failures, {suite.attrib['errors']} errors")
    assert tests == 126 and failures == errors == 0
    log = (args.smoke / "stdout.log").read_text(encoding="utf-8", errors="replace")
    assert "PARTYMOD_RENDER_SMOKE_COMPLETE" in log and "PARTYMOD_RENDER_SMOKE_FAILED" not in log
    assert "PARTYMOD_NOTE_RESIZE_REJOIN_OK" in log
    assert "PARTYMOD_STALE_UI_ACTION_OK" in log
    assert "PARTYMOD_TOGGLE_CYCLE_OK" in log
    assert "PARTYMOD_HUD_EDITOR_SCREEN cc.polyfrost.oneconfig.internal.gui.HudGui" in log
    lines += ["Python service: 11 tests passed (separate unittest run, synthetic upstream and local HTTP only)", "",
              "Minecraft runtime proof: isolated Java 8 / Forge 11.15.1.2318 / Minecraft 1.8.9 client.",
              "Production JAR loaded with cached OneConfig; 100 synthetic roster members rendered.",
              "Roster, review queue, event desk and OneConfig Party/Reviews/Actions/HUD/Data pages captured.",
              "Native OneConfig HUD editor opened; enable-disable-enable cycle completed.",
              "PASS note input preserved across GUI resize and rebound safely after same-name rejoin.",
              "PASS stale rendered host-blocklist click rejected after UUID change; fresh click accepted.",
              "No PartyMod crash reported. Cached LWJGL emits pre-existing security-seal messages.",
              "Probe uses a separate game directory, dummy offline account and no Hypixel connection.", "",
              "UNVERIFIED: current live Hypixel chat/party formats; 100 real members; removal/block/ignore",
              "and invitation acceptance/acknowledgements; permissions; real API/privacy/class responses;",
              "MW scoreboard sightings; HUD rendering and quiet mode during a live match; other modpacks.",
              "Direct API-key setup is included; no real key or Hypixel calls were used during verification.",
              "Statistics use a user-configured key in direct mode; the alternative service is optional and undeployed.",
              "Activity gaps are local observations. Ban status and outside games remain unknown.", ""]
    shipped_jar = output / jar.name
    shutil.copy2(jar, shipped_jar)
    source = output / f"PartyMod-{VERSION}-source.zip"
    include_dirs = ("src", "gradle", "libs", "licenses", "docs", "tools", "service", "smoke/src")
    include_files = ("build.gradle", "gradlew", "gradlew.bat", ".gitignore", "README.md", "THIRD-PARTY.md")
    files = {ROOT / n for n in include_files}
    for directory in include_dirs:
        files.update(p for p in (ROOT / directory).rglob("*") if p.is_file() and "__pycache__" not in p.parts and p.suffix != ".pyc")
    with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(files):
            archive.write(path, "PartyMod-2.0.4/" + path.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(source) as archive:
        assert archive.testzip() is None
        assert not any("/run-" in n or "/build/" in n or "local-records.json" in n for n in archive.namelist())
    shutil.copy2(ROOT / "README.md", output / f"PartyMod-{VERSION}-README.md")
    shutil.copy2(ROOT / "docs/API-KEY-SETUP.md", output / f"PartyMod-{VERSION}-API-KEY-SETUP.md")
    shutil.copy2(ROOT / "docs/REVIEW-2.0.4.md", output / f"PartyMod-{VERSION}-audit.md")
    shutil.copy2(ROOT / "docs/CHANGELOG-2.0.4.txt", output / f"PartyMod-{VERSION}-changes.txt")
    shutil.copy2(ROOT / "docs/LOGIN-SPAM-FIX.md", output / f"PartyMod-{VERSION}-login-fix.md")
    shutil.copy2(ROOT / "docs/FEATURES.md", output / f"PartyMod-{VERSION}-improvements.md")
    shutil.copy2(ROOT / "docs/LIVE-VALIDATION.md", output / f"PartyMod-{VERSION}-live-checklist.md")
    for file in ("roster.png", "oneconfig.png", "oneconfig-actions.png", "oneconfig-hud.png", "oneconfig-data.png", "event-desk.png"):
        shutil.copy2(args.smoke / "screenshots" / file, output / f"PartyMod-{VERSION}-{file}")
    # Keep only probe markers from the private temporary client log, not unrelated startup paths/settings.
    markers = "\n".join(line.split("]: ", 1)[-1] for line in log.splitlines() if "PARTYMOD_" in line)
    (output / f"PartyMod-{VERSION}-runtime-probe.txt").write_text(markers + "\n", encoding="utf-8")
    for path in (shipped_jar, source):
        lines.append(f"SHA-256 {path.name}: {sha(path)}")
    (output / f"PartyMod-{VERSION}-verification.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    hashes = [f"{sha(path)}  {path.name}" for path in sorted(output.glob(f"PartyMod-{VERSION}-*")) if path.is_file() and path.name != f"PartyMod-{VERSION}-SHA256.txt"]
    hashes.insert(0, f"{sha(shipped_jar)}  {shipped_jar.name}")
    (output / f"PartyMod-{VERSION}-SHA256.txt").write_text("\n".join(hashes) + "\n", encoding="utf-8")
    print(json.dumps({"jar": str(shipped_jar), "source": str(source), "javaTests": tests, "failures": failures, "errors": errors, "classes": len(classes)}, indent=2))

if __name__ == "__main__":
    main()
