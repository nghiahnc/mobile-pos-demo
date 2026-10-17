"""Package verified local outputs; Pillow is only needed for the screenshot contact sheet."""
from pathlib import Path
from datetime import datetime, timezone, timedelta
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts"
PROFILES = ["phone", "dark", "large-text", "small-phone", "tablet-portrait", "tablet-landscape", "phone-landscape"]


def luminance(hex_value):
    channels = [int(hex_value[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    r, g, b = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return .2126 * r + .7152 * g + .0722 * b


def contrast(foreground, background):
    a, b = sorted([luminance(foreground), luminance(background)])
    return round((b + .05) / (a + .05), 2)


def main():
    unit = {"tests": 0, "failures": 0, "errors": 0}
    for report in (ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        for key in unit:
            unit[key] += int(suite.get(key, 0))
    assert unit["tests"] == 15 and unit["failures"] == unit["errors"] == 0, unit
    functional = (OUT / "functional-device-tests.log").read_text(encoding="utf-8-sig")
    assert "OK (12 tests)" in functional, "Functional device tests have not passed"
    for profile in PROFILES:
        assert "OK (1 test)" in (OUT / f"profiles/{profile}.log").read_text(encoding="utf-8-sig"), profile

    lint_root = ET.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot()
    issues = lint_root.findall("issue")
    lint = {"errors": sum(i.get("severity") in ("Error", "Fatal") for i in issues),
            "warnings": sum(i.get("severity") == "Warning" for i in issues)}
    assert lint["errors"] == 0, lint
    colors = {}
    for theme in ("values", "values-night"):
        palette = {e.get("name"): e.text for e in ET.parse(ROOT / f"app/src/main/res/{theme}/colors.xml").getroot()}
        pairs = [("on_surface", "background"), ("muted", "background"), ("muted", "surface"),
                 ("primary", "soft_primary"), ("on_primary", "primary"), ("error", "soft_error")]
        colors[theme] = {f"{fg}/{bg}": contrast(palette[fg], palette[bg]) for fg, bg in pairs}
        assert min(colors[theme].values()) >= 4.5, colors[theme]
    summary = {
        "verified_at": datetime.now(timezone(timedelta(hours=7))).isoformat(timespec="seconds"),
        "unit_tests": unit, "functional_device_tests": {"tests": 12, "failures": 0},
        "visual_profiles_passed": PROFILES, "lint": lint, "text_contrast_ratios": colors,
        "device": "Medium_Phone; Android 37.1; display sizes simulated with adb wm overrides",
        "scope": "Mock payments; no bank, physical POS reader or printer integration"
    }
    (OUT / "verification-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    shutil.copy2(ROOT / "app/build/outputs/apk/debug/app-debug.apk", OUT / "NghiaPOS-debug.apk")

    from PIL import Image, ImageDraw, ImageFont
    canvas = Image.new("RGB", (1600, 1060), "#edf2ed")
    draw = ImageDraw.Draw(canvas)
    fonts = Path("C:/Windows/Fonts")
    title = ImageFont.truetype(str(fonts / "segoeuib.ttf"), 36)
    body = ImageFont.truetype(str(fonts / "segoeui.ttf"), 20)
    small = ImageFont.truetype(str(fonts / "segoeui.ttf"), 16)
    draw.text((32, 18), "NGHĨA POS  /  ANDROID DEMO", fill="#175b49", font=title)
    draw.text((32, 74), "Kotlin · Material 3 · MVVM · Retrofit · Dagger/KSP · RxJava3 · Room", fill="#56665d", font=body)
    screenshots = [("01-catalog.png", "01  Thực đơn"), ("02-cart.png", "02  Giỏ hàng"),
                   ("05-receipt-card.png", "03  Biên lai"), ("08-history.png", "04  Lịch sử")]
    for index, (filename, label) in enumerate(screenshots):
        image = Image.open(OUT / "screenshots" / filename).convert("RGB")
        image.thumbnail((360, 800), Image.Resampling.LANCZOS)
        x, y = 32 + index * 392, 126
        draw.rounded_rectangle((x - 3, y - 3, x + 363, y + 803), radius=15, fill="#d8e0d9")
        canvas.paste(image, (x, y))
        draw.text((x, 947), label, fill="#1b2923", font=body)
    draw.text((32, 1014), "Ảnh chụp app chạy trên Android emulator · Thanh toán mô phỏng · Không chuyển tiền thật", fill="#56665d", font=small)
    canvas.save(OUT / "preview.png")

    source_zip = OUT / "NghiaPOS-source.zip"
    with zipfile.ZipFile(source_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(ROOT.rglob("*")):
            if not path.is_file():
                continue
            relative = path.relative_to(ROOT)
            parts = relative.parts
            if any(part in (".gradle", ".kotlin", ".git", ".idea", "build", "__pycache__") for part in parts):
                continue
            if path.name == "local.properties" or path.suffix == ".iml":
                continue
            if parts[0] == "artifacts":
                evidence_dir = len(parts) > 1 and parts[1] in ("screenshots", "profiles", "unit-tests")
                evidence_file = path.name in ("QA.md", "preview.png", "verification-summary.json", "lint-results-debug.html", "lint-results-debug.txt", "functional-device-tests.log", "final-build.log")
                if not (evidence_dir or evidence_file):
                    continue
            archive.write(path, Path("NghiaPOS") / relative)
    sums = "".join(f"{hashlib.sha256((OUT / name).read_bytes()).hexdigest()}  {name}\n"
                   for name in ("NghiaPOS-debug.apk", "NghiaPOS-source.zip"))
    (OUT / "SHA256SUMS.txt").write_text(sums, encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"Packaged APK, source ZIP, preview and hashes in {OUT}")


if __name__ == "__main__":
    main()
