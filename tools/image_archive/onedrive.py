"""Consumer Graph app-folder adapter. No scope escalation or implicit connection."""
from __future__ import annotations

from hashlib import sha256
import time
import uuid
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from urllib.parse import quote, urlparse
import requests
from .archive import IntegrityError

GRAPH = 'https://graph.microsoft.com/v1.0'
SCOPES = ['Files.ReadWrite.AppFolder']
CHUNK = 10 * 320 * 1024  # Graph requires non-final chunks to be a multiple of 320 KiB.


class GraphError(RuntimeError):
    def __init__(self, message, code='graph_error'):
        super().__init__(message)
        self.code = code


class OneDriveBackend:
    def __init__(self, token_provider, *, authorized=False, session=None, sleep=time.sleep, attempts=4):
        self.token_provider = token_provider
        self.authorized = authorized
        self.session = session or requests.Session()
        self.sleep = sleep
        self.attempts = attempts
        self.root_id = None

    def _request(self, method, url, *, authenticated=True, allowed=(200,), **kwargs):
        if not self.authorized:
            raise GraphError('explicit Microsoft connection authorization required')
        if authenticated and not url.startswith(GRAPH + '/'):
            raise GraphError('refusing credential forwarding')
        if urlparse(url).scheme != 'https':
            raise GraphError('HTTPS required')
        headers = dict(kwargs.pop('headers', {}))
        for attempt in range(self.attempts):
            if authenticated:
                headers['Authorization'] = 'Bearer ' + self.token_provider()
            try:
                response = self.session.request(method, url, headers=headers, timeout=(15, 120),
                                                allow_redirects=False, **kwargs)
            except requests.RequestException:
                if attempt + 1 == self.attempts:
                    raise GraphError('transport failure; pending') from None
                self.sleep(min(2 ** attempt, 30))
                continue
            if response.status_code in allowed:
                return response
            if response.status_code in (429, 500, 502, 503, 504):
                if attempt + 1 < self.attempts:
                    try:
                        delay = float(response.headers.get('Retry-After', 2 ** attempt))
                    except ValueError:
                        try:
                            retry_at = parsedate_to_datetime(response.headers['Retry-After'])
                            delay = (retry_at - datetime.now(timezone.utc)).total_seconds()
                        except (ValueError, TypeError, KeyError):
                            delay = 2 ** attempt
                    # Refuse an excessive wait instead of retrying earlier than requested.
                    if delay > 60:
                        raise GraphError('retry later; pending')
                    self.sleep(max(0, delay))
                    continue
            # Do not include response bodies, signed URLs or bearer tokens in errors.
            raise GraphError(f'Graph HTTP {response.status_code}; pending (no permission escalation)',
                             code=f'graph_http_{response.status_code}')
        raise GraphError('retry budget exhausted')

    def _root(self):
        if self.root_id is None:
            self.root_id = self._request('GET', GRAPH + '/me/drive/special/approot').json()['id']
        return GRAPH + '/me/drive/items/' + quote(self.root_id, safe='')

    @staticmethod
    def _key(key):
        if key.startswith('/') or any(p in ('', '.', '..') for p in key.split('/')):
            raise ValueError('unsafe archive key')
        return 'WatchAlign/' + key

    def _item(self, key):
        return self._root() + ':/' + quote(self._key(key), safe='/')

    def _folders(self, key):
        parent = self._root()
        for name in self._key(key).split('/')[:-1]:
            response = self._request('POST', parent + '/children', allowed=(200, 201, 409),
                                     json={'name': name, 'folder': {}, '@microsoft.graph.conflictBehavior': 'fail'})
            if response.status_code == 409:
                response = self._request('GET', parent + ':/' + quote(name, safe=''))
            doc = response.json()
            if 'folder' not in doc:
                raise IntegrityError('folder path conflict')
            parent = GRAPH + '/me/drive/items/' + quote(doc['id'], safe='')

    def get(self, key):
        response = self._request('GET', self._item(key) + ':/content', allowed=(200, 302, 404))
        if response.status_code == 404:
            raise FileNotFoundError(key)
        if response.status_code == 302:
            # Download URLs are signed capabilities; never send Graph bearer credentials.
            response = self._request('GET', response.headers['Location'], authenticated=False)
        return response.content

    def put(self, key, data):
        try:
            current = self.get(key)
        except FileNotFoundError:
            current = None
        if current is not None:
            if current != data:
                raise IntegrityError('immutable remote object conflict')
            return
        self._folders(key)
        response = self._request('POST', self._item(key) + ':/createUploadSession', allowed=(200, 201, 409),
                                 json={'item': {'@microsoft.graph.conflictBehavior': 'fail'}})
        if response.status_code == 409:
            if self.get(key) != data:
                raise IntegrityError('concurrent object conflict')
            return
        upload_url = response.json()['uploadUrl']
        offset = 0
        while offset < len(data):
            end = min(offset + CHUNK, len(data))
            response = self._request('PUT', upload_url, authenticated=False, allowed=(200, 201, 202, 409, 416),
                                     data=data[offset:end], headers={'Content-Length': str(end-offset),
                                     'Content-Range': f'bytes {offset}-{end-1}/{len(data)}'})
            if response.status_code == 409:
                break  # Another writer may have completed; verify bytes below.
            if response.status_code == 416:
                # A retried PUT may already have committed. Ask for server progress.
                try:
                    progress = self._request('GET', upload_url, authenticated=False).json()
                except GraphError:
                    break  # Completed session may have disappeared; verify final object.
                next_offset = int(progress['nextExpectedRanges'][0].split('-')[0])
                if next_offset <= offset or next_offset > len(data):
                    raise GraphError('invalid upload progress')
                offset = next_offset
                continue
            if response.status_code == 202:
                next_offset = int(response.json()['nextExpectedRanges'][0].split('-')[0])
                if next_offset != end:
                    raise GraphError('unexpected upload progress; retry from pending original')
            elif end != len(data):
                raise GraphError('premature upload completion')
            offset = end
        if self.get(key) != data:
            raise IntegrityError('remote verification failed')

    def list_snapshots(self):
        url = self._item('manifests/runs') + ':/children'
        found = []
        while url:
            response = self._request('GET', url, allowed=(200, 404))
            if response.status_code == 404:
                break
            doc = response.json()
            found.extend('manifests/runs/' + x['name'] for x in doc.get('value', []) if 'file' in x)
            url = doc.get('@odata.nextLink')
        return found

    def list_objects(self):
        pending = [('objects', self._item('objects') + ':/children')]
        found = []
        while pending:
            prefix, url = pending.pop()
            while url:
                response = self._request('GET', url, allowed=(200, 404))
                if response.status_code == 404:
                    break
                doc = response.json()
                for item in doc.get('value', []):
                    name = item['name']
                    if '/' in name or name in ('.', '..'):
                        raise IntegrityError('invalid remote object name')
                    key = prefix + '/' + name
                    if 'folder' in item:
                        pending.append((key, self._item(key) + ':/children'))
                    elif 'file' in item:
                        found.append(key)
                url = doc.get('@odata.nextLink')
        return found

    def connectivity_test(self):
        # No generic delete API: cleanup can only address this newly generated test key.
        key = 'smoke/' + uuid.uuid4().hex + '.txt'
        data = ('Watch Align synthetic connectivity test ' + uuid.uuid4().hex).encode()
        self.put(key, data)
        if self.get(key) != data:
            raise IntegrityError('synthetic restore differs')
        self._request('DELETE', self._item(key), allowed=(204,))
        return {'status': 'verified_and_cleaned', 'sha256': sha256(data).hexdigest(), 'synthetic': True}
