#!/usr/bin/env python3
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import os
import urllib.parse

ROOT = Path(__file__).resolve().parent
PUBLIC = ROOT / "public"

class Handler(SimpleHTTPRequestHandler):
    def translate_path(self, path: str) -> str:
        parsed = urllib.parse.urlparse(path)
        rel = urllib.parse.unquote(parsed.path.lstrip("/"))
        if rel in ("", "index.html"):
            return str(PUBLIC / "index.html")
        target = (PUBLIC / rel).resolve()
        try:
            target.relative_to(PUBLIC.resolve())
        except ValueError:
            return str(PUBLIC / "index.html")
        if target.is_file():
            return str(target)
        return str(PUBLIC / "index.html")

    def guess_type(self, path: str) -> str:
        if path.endswith(".apk"):
            return "application/vnd.android.package-archive"
        return super().guess_type(path)

    def do_POST(self) -> None:
        # This deployment serves the release APK only. Return a client error for
        # upload smoke probes so absence of upload support is not reported as a
        # server failure.
        self.send_error(404, "No upload endpoint in APK release deployment")

    def end_headers(self) -> None:
        self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()

    def log_message(self, fmt: str, *args) -> None:
        print("[http] " + fmt % args, flush=True)

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    PUBLIC.mkdir(exist_ok=True)
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"Serving BLE Data Collector release page on 0.0.0.0:{port}", flush=True)
    server.serve_forever()
