"""One-time hand-over of the OneDrive connection from the Codespace to protected GitHub Actions.

    python -m tools.image_archive.setup_ci --cache /tmp/watch-align-private/msal-cache.json [--apply]

Without --apply it only prints what it would do and the account pin (a SHA-256, never the email address).
With --apply, using your own `gh auth login` session (repository admin), it:
  1. REQUIRES the owner to pre-create the protected `onedrive-archive` environment in GitHub UI;
     this script NEVER changes the reviewers, bypass settings, or allowed branches;
  2. sets environment variables ONEDRIVE_CLIENT_ID and ONEDRIVE_ACCOUNT_SHA256 (owner pin);
  3. stores the MSAL cache as the encrypted environment secret ONEDRIVE_MSAL_CACHE (read from the file, piped to gh,
     never printed or passed on a command line).
The cache-writer token (ONEDRIVE_CACHE_WRITE_TOKEN) must be created by you on github.com; see docs/image-archive/AUTOMATION.md.
Run it only after the Codespace transfer has finished, then stop using the Codespace copy of the cache.
"""
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

from .auth import ENVIRONMENT, account_fingerprint, client

BRANCH = "experiment/archive-first-onedrive-20261023"
CLIENT_ID = "06a3bb10-a5d3-4dde-9654-1e61de7cd064"


def pin_from_cache(cache_text, client_id=CLIENT_ID, msal_module=None):
    app, _ = client(client_id, cache_text, msal_module=msal_module)
    accounts = app.get_accounts()
    if len(accounts) != 1:
        raise SystemExit("the cache must contain exactly one Microsoft account")
    return account_fingerprint(accounts[0].get("username"))


def plan(repo, client_id, pin):
    """Update variables/secrets only; the owner manages all environment protection in GitHub UI.

    Never create or PUT the environment here: a PUT that omits reviewer rules can
    unintentionally reset protection. The owner must configure required reviewers,
    disabled admin bypass, and the exact allowed branch *before* --apply.
    """
    return [
        (["gh", "variable", "set", "ONEDRIVE_CLIENT_ID", "--env", ENVIRONMENT, "--repo", repo, "--body", client_id], None),
        (["gh", "variable", "set", "ONEDRIVE_ACCOUNT_SHA256", "--env", ENVIRONMENT, "--repo", repo, "--body", pin], None),
        (["gh", "secret", "set", "ONEDRIVE_MSAL_CACHE", "--env", ENVIRONMENT, "--repo", repo], "<cache file>"),
    ]


def main(argv=None, run=subprocess.run):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cache", type=Path, required=True)
    ap.add_argument("--repo", default="Biggregw/watch-align")
    ap.add_argument("--client-id", default=CLIENT_ID)
    ap.add_argument("--apply", action="store_true",
                    help="Only after the protected environment is created and verified in the GitHub UI")
    a = ap.parse_args(argv)
    text = a.cache.read_text(encoding="utf-8")
    pin = pin_from_cache(text, a.client_id)
    print("Owner account pin (SHA-256 of the sign-in name):", pin)
    steps = plan(a.repo, a.client_id, pin)
    print("This helper does NOT configure deployment protection. Confirm Required reviewers,")
    print("administrator bypass OFF, and the exact allowed branch in GitHub before --apply.")
    for cmd, body in steps:
        print(("running: " if a.apply else "would run: ") + " ".join(cmd))
        if not a.apply:
            continue
        stdin = text if cmd[:3] == ["gh", "secret", "set"] else body
        result = run(cmd, input=stdin, text=True, capture_output=True)
        if result.returncode and "already exists" not in (result.stderr or ""):
            raise SystemExit(f"step failed: {' '.join(cmd[:4])} (gh exit {result.returncode}); nothing secret was printed")
    if not a.apply:
        print("Dry run only. Re-run with --apply after the Codespace transfer has finished.")
    return pin


if __name__ == "__main__":
    main()
