"""Read-only access to the curated Watch Align Dropbox archive, or to an offline cache of it.

The research mode reads ONLY from the Dropbox archive root (default
"/Watch Align/Apps/Watch Align Archive/WatchAlign"): "Suitable Photos/", "Suitable Photos/_audit/" and the
source and review index under "Research/". No dealer scraping, public discovery or GitHub-hosted photographs.

Dropbox: only three read endpoints are ever called (list_folder, list_folder/continue, download) plus the OAuth token
refresh. Any other endpoint is refused in code. Credentials come from the environment, never arguments or files in
the repository, and are never printed:
    DROPBOX_REFRESH_TOKEN + DROPBOX_APP_KEY (+ DROPBOX_APP_SECRET when the app is not PKCE)   preferred
    DROPBOX_ACCESS_TOKEN                                                                       short-lived alternative
Use a scoped Dropbox app with only files.metadata.read and files.content.read.

Offline: a local cache directory that mirrors the archive's relative paths and holds only the SHA-256-verified
approved selection (written by `cache_approved`), the audit records and the index. The same verification gates run
again on every read, so a cache cannot add, replace or relabel a photograph.
"""
from __future__ import annotations

from hashlib import sha256
import json
import os
from pathlib import Path, PurePosixPath
import re

DEFAULT_ROOT = "/Watch Align/Apps/Watch Align Archive/WatchAlign"
API = "https://api.dropboxapi.com/2/"
CONTENT = "https://content.dropboxapi.com/2/"
TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
READ_ENDPOINTS = {API + "files/list_folder", API + "files/list_folder/continue", CONTENT + "files/download"}
HASH = re.compile(r"^[0-9a-f]{64}$")


class SourceError(RuntimeError):
    """The archive could not be read safely; the run stops (fail closed). Never contains credentials."""


def _rel(path):
    p = PurePosixPath(str(path))
    if p.is_absolute() or any(part in ("", ".", "..") for part in p.parts):
        raise SourceError(f"unsafe archive path: {path!r}")
    return p.as_posix()


class DropboxSource:
    """list(folder) -> [(name, is_file, size)]; read(path) -> bytes. Paths are relative to the archive root."""
    mode = "dropbox"

    def __init__(self, root=DEFAULT_ROOT, environ=None, session=None):
        import requests
        self.root = "/" + str(root).strip("/")
        self.env = environ if environ is not None else os.environ
        self.session = session or requests.Session()
        self._token = self.env.get("DROPBOX_ACCESS_TOKEN") or None
        if not self._token and not (self.env.get("DROPBOX_REFRESH_TOKEN") and self.env.get("DROPBOX_APP_KEY")):
            raise SourceError("Dropbox credentials are not configured (set DROPBOX_REFRESH_TOKEN and DROPBOX_APP_KEY, "
                              "or DROPBOX_ACCESS_TOKEN, in the environment)")

    def describe(self):
        return {"mode": self.mode, "root": self.root}

    def _refresh(self):
        data = {"grant_type": "refresh_token", "refresh_token": self.env["DROPBOX_REFRESH_TOKEN"],
                "client_id": self.env["DROPBOX_APP_KEY"]}
        if self.env.get("DROPBOX_APP_SECRET"):
            data["client_secret"] = self.env["DROPBOX_APP_SECRET"]
        r = self.session.post(TOKEN_URL, data=data, timeout=30)
        if r.status_code != 200 or "access_token" not in (r.json() or {}):
            raise SourceError(f"Dropbox token refresh failed (HTTP {r.status_code}); re-authorise the read-only app")
        self._token = r.json()["access_token"]

    def _call(self, url, *, json_body=None, arg=None):
        if url not in READ_ENDPOINTS:
            raise SourceError("refusing a Dropbox call outside the read-only allowlist")
        for attempt in (0, 1):
            if not self._token:
                self._refresh()
            headers = {"Authorization": "Bearer " + self._token}
            if arg is not None:
                headers["Dropbox-API-Arg"] = json.dumps(arg, ensure_ascii=True)
                r = self.session.post(url, headers=headers, timeout=(15, 300))
            else:
                r = self.session.post(url, headers=headers, json=json_body, timeout=(15, 120))
            if r.status_code == 401 and attempt == 0 and self.env.get("DROPBOX_REFRESH_TOKEN"):
                self._token = None
                continue
            if r.status_code == 409:
                raise FileNotFoundError("not found in the Dropbox archive")
            if r.status_code != 200:
                raise SourceError(f"Dropbox read failed (HTTP {r.status_code})")
            return r
        raise SourceError("Dropbox authorisation failed")

    def list(self, folder):
        path = self.root + "/" + _rel(folder)
        try:
            body = self._call(API + "files/list_folder", json_body={"path": path, "recursive": False}).json()
        except FileNotFoundError:
            return []
        out = []
        while True:
            for e in body.get("entries", []):
                name = e.get("name", "")
                if "/" in name or name in ("", ".", ".."):
                    raise SourceError("unexpected entry name in the Dropbox archive")
                out.append((name, e.get(".tag") == "file", int(e.get("size") or 0)))
            if not body.get("has_more"):
                return out
            body = self._call(API + "files/list_folder/continue", json_body={"cursor": body["cursor"]}).json()

    def read(self, path):
        return self._call(CONTENT + "files/download", arg={"path": self.root + "/" + _rel(path)}).content


class CacheSource:
    """The offline cache: same interface, read-only, never touches the network."""
    mode = "local_cache"

    def __init__(self, directory):
        self.dir = Path(directory).resolve()
        if not (self.dir / "CACHE_MANIFEST.json").is_file():
            raise SourceError("not a Watch Align research cache (CACHE_MANIFEST.json missing)")

    def describe(self):
        return {"mode": self.mode, "cache": str(self.dir)}

    def list(self, folder):
        d = self.dir / _rel(folder)
        if not d.is_dir():
            return []
        return sorted((p.name, p.is_file(), p.stat().st_size if p.is_file() else 0) for p in d.iterdir())

    def read(self, path):
        p = self.dir / _rel(path)
        if not p.is_file():
            raise FileNotFoundError(path)
        return p.read_bytes()


def cache_approved(source, accepted, support_files, directory):
    """Write the offline cache: support files (audit, index) and ONLY the accepted photographs, each re-verified.

    `accepted` are records produced by the verification gates (sha256 + archive path). The cache never receives a
    photograph outside the approved selection and refuses to overwrite a different file.
    """
    root = Path(directory).resolve()
    root.mkdir(parents=True, exist_ok=True)
    written = []

    def put(rel, data):
        target = root / _rel(rel)
        if target.exists():
            if target.read_bytes() != data:
                raise SourceError(f"cache already holds a different {rel}; refusing to overwrite")
            return
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp = target.with_suffix(target.suffix + ".part")
        tmp.write_bytes(data)
        tmp.replace(target)
        written.append(rel)

    for rel, data in support_files.items():
        put(rel, data)
    for rec in accepted:
        data = source.read(rec["archive_path"])
        if sha256(data).hexdigest() != rec["sha256"]:
            raise SourceError(f"{rec['archive_path']} failed SHA-256 verification; cache not completed")
        put(rec["archive_path"], data)
    manifest = {"schema": "watch-align-research-cache/1", "source": source.describe(),
                "photos": sorted(r["sha256"] for r in accepted),
                "support_files": {rel: sha256(d).hexdigest() for rel, d in support_files.items()}}
    (root / "CACHE_MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return {"cached_photos": len(accepted), "files_written": len(written)}
