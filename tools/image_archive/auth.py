"""Explicit phone-friendly device consent; external MSAL cache, never token logging.

Actions renewal uses an explicitly supplied secret writer. Without durable external
persistence this fails before using a changed cache; static refresh tokens are not
represented as permanent unattended credentials.
"""
from pathlib import Path
import os
from .archive import atomic_write
from .onedrive import SCOPES


def client(client_id, cache_text='', *, msal_module=None):
    if msal_module is None:
        import msal as msal_module
    cache = msal_module.SerializableTokenCache()
    if cache_text:
        cache.deserialize(cache_text)
    app = msal_module.PublicClientApplication(client_id, authority='https://login.microsoftonline.com/consumers', token_cache=cache)
    return app, cache


def token_provider(app, cache, save_cache):
    pending_cache = None
    def acquire():
        nonlocal pending_cache
        if pending_cache is not None:
            save_cache(pending_cache)
            pending_cache = None
        accounts = app.get_accounts()
        if len(accounts) != 1:
            raise RuntimeError('exactly one consented consumer account required; reauthorize')
        try:
            result = app.acquire_token_silent(SCOPES, account=accounts[0]) or {}
        except Exception:
            raise RuntimeError('Microsoft token renewal failed; reauthorize or retry') from None
        if cache.has_state_changed:
            # MSAL serialize() clears has_state_changed; keep unsaved text across failures.
            pending_cache = cache.serialize()
            save_cache(pending_cache)
            pending_cache = None
        if 'access_token' not in result:
            raise RuntimeError('Microsoft consent expired or revoked; reauthorize')
        return result['access_token']
    return acquire


def external_cache_writer(path, repository_root):
    path = Path(path).resolve()
    if path.is_relative_to(Path(repository_root).resolve()):
        raise ValueError('credential cache must be outside the repository')
    def save(text):
        # mkstemp in atomic_write creates 0600; enforce for existing files as well.
        atomic_write(path, text.encode())
        os.chmod(path, 0o600)
    return save


def device_login(app, cache, save_cache, display=print):
    flow = app.initiate_device_flow(scopes=SCOPES)
    if not flow.get('user_code'):
        raise RuntimeError('device authorization unavailable')
    # Display only user interaction fields, never raw flow/token payloads.
    # Use Microsoft's actual verification URI: consumer sessions return /link, not /devicelogin.
    display('Open ' + flow.get('verification_uri', 'https://www.microsoft.com/link') + ' and enter: ' + flow['user_code'])
    result = app.acquire_token_by_device_flow(flow)
    if 'access_token' not in result:
        raise RuntimeError('Microsoft consent failed')
    save_cache(cache.serialize())


ENVIRONMENT = 'onedrive-archive'


def github_cache_writer(repository, secret_name='ONEDRIVE_MSAL_CACHE', environment=ENVIRONMENT):
    """Persist rotated cache using gh's encrypted ENVIRONMENT-secret API.

    The cache is an environment secret of `onedrive-archive`, so only jobs that pass
    that environment's protection rules (required reviewers) can read it; a repository
    secret would be readable by any workflow on any branch of this public repository.
    GH_TOKEN must be a fine-grained credential restricted to this repository with
    Environments: read and write. No token is passed as an argument or emitted on error.
    """
    import re
    import subprocess
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('invalid repository')
    if secret_name != 'ONEDRIVE_MSAL_CACHE' or environment != ENVIRONMENT:
        raise ValueError('unexpected secret name or environment')
    def save(text):
        result = subprocess.run(['gh', 'secret', 'set', secret_name, '--env', environment, '--repo', repository],
                                input=text, text=True, capture_output=True)
        if result.returncode:
            raise RuntimeError('encrypted cache renewal failed; token use withheld')
    return save
