"""Research archive CLI. Local by default; real-photo OneDrive sync not enabled."""
import argparse
import csv
import json
import os
from pathlib import Path
from .archive import Archive
from .auth import client, device_login, external_cache_writer, github_cache_writer, token_provider
from .diagnostics import LegacyGMTDiagnostics
from .integration import restore_acquired, submit_acquired
from .inventory import inventory, native_inventory, write_report
from .onedrive import OneDriveBackend
from .screening import Screener


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('.image-archive'))
    sub = parser.add_subparsers(dest='command', required=True)
    assess = sub.add_parser('assess')
    assess.add_argument('image', type=Path)
    assess.add_argument('--family', required=True)
    assess.add_argument('--gmt-diagnostics', action='store_true')
    inv = sub.add_parser('inventory')
    inv.add_argument('--repo', type=Path, default=Path.cwd())
    inv.add_argument('--scan', type=Path, action='append', default=[])
    inv.add_argument('--output', type=Path, required=True)
    inv.add_argument('--native-diagnostics', action='store_true', help='Inspect available originals using JDK17/model-specific harness; read-only')
    ingest = sub.add_parser('ingest-local')
    ingest.add_argument('manifest', type=Path)
    ingest.add_argument('--images-root', type=Path, required=True)
    restore = sub.add_parser('restore-local')
    restore.add_argument('manifest', type=Path)
    restore.add_argument('--images-root', type=Path, required=True)
    for command in ('report', 'reconcile', 'sync-local'):
        sub.add_parser(command)
    for command in ('authorize', 'smoke'):
        p = sub.add_parser(command)
        p.add_argument('--authorized', action='store_true', required=True,
                       help='Use ONLY after owner authorizes connection; smoke uses synthetic bytes only')
        p.add_argument('--client-id', default=os.environ.get('ONEDRIVE_CLIENT_ID'))
        p.add_argument('--cache', type=Path)
        p.add_argument('--renew-github-secret', metavar='OWNER/REPO')
    args = parser.parse_args()
    if args.command == 'assess':
        from .faceon import production_screener
        screen = Screener(LegacyGMTDiagnostics()) if args.gmt_diagnostics else production_screener()
        result = screen.assess_candidate(args.image, args.family).record()
    elif args.command == 'inventory':
        result = (native_inventory if args.native_diagnostics else inventory)(args.repo, args.scan)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        write_report(args.output, result)
        result = result['total']
    elif args.command in ('authorize', 'smoke'):
        if not args.client_id:
            parser.error('ONEDRIVE_CLIENT_ID is required')
        if args.renew_github_secret:
            cache_text = os.environ.get('ONEDRIVE_MSAL_CACHE', '')
            save = github_cache_writer(args.renew_github_secret)
        elif args.cache:
            save = external_cache_writer(args.cache, Path(__file__).resolve().parents[2])
            cache_text = args.cache.read_text() if args.cache.exists() else ''
        else:
            parser.error('external --cache or --renew-github-secret is required')
        app, cache = client(args.client_id, cache_text)
        if args.command == 'authorize':
            if os.environ.get('CI'):
                parser.error('interactive authorization must run outside Actions')
            device_login(app, cache, save)
            result = {'status': 'consent_saved'}
        else:
            drive = OneDriveBackend(token_provider(app, cache, save), authorized=True)
            result = drive.connectivity_test()
    else:
        archive = Archive(args.root)
        if args.command == 'ingest-local':
            result = submit_acquired(archive, args.images_root, list(csv.DictReader(args.manifest.open())))
        elif args.command == 'restore-local':
            result = restore_acquired(archive, args.manifest, args.images_root)
        elif args.command == 'report':
            result = archive.report()
        elif args.command == 'reconcile':
            result = archive.reconcile()
        else:
            result = archive.sync()
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
