"""Regenerate the sample PNGs and link manifest with Python qrcode[pil]."""

import base64
import json
from pathlib import Path
from urllib.parse import quote

import qrcode


payload = {"name": "Taylor", "token": "12025550123"}
data = quote(base64.b64encode(json.dumps(payload, separators=(",", ":")).encode()).decode(), safe="")
links = {
    "canonical": f"https://enroll.zellepay.com/qr-codes?data={data}",
    "bridge-placeholder": f"https://qr.example.com/qr-codes?data={data}",
}

fixture_dir = Path(__file__).resolve().parent
for name, url in links.items():
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=4)
    qr.add_data(url, optimize=0)
    qr.make()
    qr.make_image(fill_color="black", back_color="white").save(fixture_dir / f"{name}.png")

manifest = {"payload": payload, "links": links}
(fixture_dir / "links.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
