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


CACHE_SCHEMA = "watch-align-research-cache/2"
INVENTORY = "FOLDER_INVENTORY.json"
INVENTORY_SCHEMA = "watch-align-folder-inventory/1"
PHOTO_NAME = re.compile(r"^Suitable Photos/([^/_][^/]*)/([0-9a-f]{64})\.(jpg|jpeg|png|webp|tif)$")


class CacheSource:
    """The offline cache: same interface, read-only, never touches the network.

    A research cache (CACHE_MANIFEST.json schema watch-align-research-cache/2) holds the image bytes of the approved
    photographs only, plus a metadata-only inventory (FOLDER_INVENTORY.json) of EVERY entry of the archive's
    "Suitable Photos/<model>/" folders, so the verification gates and the source-snapshot check see the archive's
    exact curated population (for example 82 folder entries, of which 74 are measured) without copying the image bytes
    of excluded references. The whole cache is verified when opened (fail closed):
      - every support file (index, audit records) matches its recorded SHA-256, and the inventory matches its hash;
      - every retained photograph is listed in the inventory, its bytes hash to the SHA-256 in its name and its size
        matches; every excluded entry has NO image bytes in the cache;
      - nothing else is in the cache.
    Listings of the Suitable Photos model folders come from the inventory; a metadata-only entry cannot be read.
    A directory whose manifest has no schema is treated as a plain full mirror of the archive (all bytes present,
    e.g. test fixtures) and listed from disk; the gates and snapshot check apply unchanged.
    """

    def __init__(self, directory):
        self.dir = Path(directory).resolve()
        mf = self.dir / "CACHE_MANIFEST.json"
        if not mf.is_file():
            raise SourceError("not a Watch Align research cache (CACHE_MANIFEST.json missing)")
        self.manifest = json.loads(mf.read_text(encoding="utf-8") or "{}")
        self.inventory = None
        if self.manifest.get("schema") is not None:
            self._verify()
        self.mode = "local_cache" if self.inventory is not None else "local_mirror"

    def _verify(self):
        m = self.manifest
        if m.get("schema") != CACHE_SCHEMA:
            raise SourceError(f"unsupported cache schema {m.get('schema')!r} (expected {CACHE_SCHEMA})")
        allowed = {"CACHE_MANIFEST.json", INVENTORY}
        for rel, digest in (m.get("support_files") or {}).items():
            p = self.dir / _rel(rel)
            if not p.is_file() or sha256(p.read_bytes()).hexdigest() != digest:
                raise SourceError(f"cache support file {rel} is missing or differs from its recorded SHA-256")
            allowed.add(_rel(rel))
        inv_path = self.dir / INVENTORY
        if not inv_path.is_file():
            raise SourceError("cache has no folder inventory")
        raw = inv_path.read_bytes()
        if sha256(raw).hexdigest() != m.get("inventory_sha256"):
            raise SourceError("folder inventory differs from the SHA-256 recorded in the cache manifest")
        inv = json.loads(raw)
        if inv.get("schema") != INVENTORY_SCHEMA:
            raise SourceError("unsupported folder inventory schema")
        seen, retained = set(), set()
        for e in inv.get("entries", []):
            path = _rel(e.get("path", ""))
            match = PHOTO_NAME.fullmatch(path)
            if not match or path in seen:
                raise SourceError(f"invalid or duplicate inventory entry {path!r}")
            seen.add(path)
            p = self.dir / path
            if e.get("bytes_retained") is True:
                data = p.read_bytes() if p.is_file() else None
                if data is None or sha256(data).hexdigest() != match.group(2) or len(data) != int(e.get("size", -1)):
                    raise SourceError(f"retained photograph {path} is missing or fails SHA-256/size verification")
                retained.add(match.group(2))
                allowed.add(path)
            elif e.get("bytes_retained") is False:
                if p.exists():
                    raise SourceError(f"{path} is metadata-only in the inventory but image bytes are present")
            else:
                raise SourceError(f"inventory entry {path} does not say whether its bytes are retained")
        if retained != set(m.get("retained_photos") or []):
            raise SourceError("retained photographs differ between the cache manifest and the folder inventory")
        for p in self.dir.rglob("*"):
            if p.is_file() and p.relative_to(self.dir).as_posix() not in allowed:
                raise SourceError(f"unexpected file in the cache: {p.relative_to(self.dir).as_posix()}")
        self.inventory = inv["entries"]

    def describe(self):
        d = {"mode": self.mode, "cache": str(self.dir)}
        if self.inventory is not None:
            d.update(inventory_entries=len(self.inventory),
                     retained_photos=sum(e["bytes_retained"] for e in self.inventory),
                     inventory_sha256=self.manifest["inventory_sha256"])
        return d

    def list(self, folder):
        rel = _rel(folder)
        if self.inventory is not None and (rel == "Suitable Photos" or
                                           (rel.startswith("Suitable Photos/") and rel.count("/") == 1
                                            and not rel.split("/")[1].startswith("_"))):
            if rel == "Suitable Photos":
                names = {(e["path"].split("/")[1], False, 0) for e in self.inventory}
                d = self.dir / rel
                if d.is_dir():   # _audit and any other underscore folder come from disk
                    names |= {(p.name, False, 0) for p in d.iterdir() if p.is_dir() and p.name.startswith("_")}
                return sorted(names)
            return sorted((e["path"].rsplit("/", 1)[1], True, int(e["size"])) for e in self.inventory
                          if e["path"].rsplit("/", 1)[0] == rel)
        d = self.dir / rel
        if not d.is_dir():
            return []
        return sorted((p.name, p.is_file(), p.stat().st_size if p.is_file() else 0) for p in d.iterdir())

    def read(self, path):
        p = self.dir / _rel(path)
        if not p.is_file():
            raise FileNotFoundError(f"{path} (not retained in the cache)")
        return p.read_bytes()


def folder_inventory(source):
    """Every entry of the archive's Suitable Photos model folders, as metadata (path, size)."""
    entries = []
    for folder, is_file, _ in source.list("Suitable Photos"):
        if is_file or folder.startswith("_"):
            continue
        for name, f, size in source.list(f"Suitable Photos/{folder}"):
            if f and PHOTO_NAME.fullmatch(f"Suitable Photos/{folder}/{name}"):
                entries.append({"path": f"Suitable Photos/{folder}/{name}", "size": int(size)})
    return entries


def cache_approved(source, accepted, support_files, directory, inventory=None):
    """Write the offline cache: support files (audit, index), a metadata-only inventory of every Suitable Photos folder
    entry, and the image bytes of ONLY the accepted photographs, each re-verified. Refuses to overwrite a different
    file; the result is verified by opening it."""
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
    keep = {r["archive_path"]: r for r in accepted}
    for rec in accepted:
        data = source.read(rec["archive_path"])
        if sha256(data).hexdigest() != rec["sha256"]:
            raise SourceError(f"{rec['archive_path']} failed SHA-256 verification; cache not completed")
        put(rec["archive_path"], data)
    entries = inventory if inventory is not None else folder_inventory(source)
    missing = set(keep) - {e["path"] for e in entries}
    if missing:
        raise SourceError(f"accepted photographs absent from the folder inventory: {sorted(missing)[:3]}")
    inv = {"schema": INVENTORY_SCHEMA, "root": "Suitable Photos",
           "entries": [{"path": e["path"], "size": int(e["size"]), "bytes_retained": e["path"] in keep}
                       for e in sorted(entries, key=lambda e: e["path"])]}
    inv_raw = (json.dumps(inv, indent=1) + "\n").encode()
    put(INVENTORY, inv_raw)
    manifest = {"schema": CACHE_SCHEMA, "source": source.describe(),
                "retained_photos": sorted(r["sha256"] for r in accepted),
                "inventory_sha256": sha256(inv_raw).hexdigest(),
                "support_files": {rel: sha256(d).hexdigest() for rel, d in support_files.items()}}
    (root / "CACHE_MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    opened = CacheSource(root)        # verify what was written
    return {"cached_photos": len(accepted), "inventory_entries": len(inv["entries"]),
            "files_written": len(written), "verified": opened.mode == "local_cache"}
