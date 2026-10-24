import pytest
import requests
from tools.image_archive.onedrive import OneDriveBackend, GraphError, GRAPH, CHUNK
from tools.image_archive.archive import IntegrityError
from tools.image_archive.auth import token_provider, external_cache_writer, device_login


class Response:
    def __init__(self, status=200, doc=None, content=b'', headers=None):
        self.status_code, self.doc, self.content, self.headers = status, doc or {}, content, headers or {}
    def json(self):
        return self.doc


class Session:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = []
    def request(self, method, url, **kwargs):
        self.calls.append((method, url, kwargs))
        r = next(self.responses)
        if isinstance(r, Exception):
            raise r
        return r


def backend(responses):
    session = Session(responses)
    return OneDriveBackend(lambda: 'secret', authorized=True, session=session, sleep=lambda _: None), session


@pytest.mark.parametrize('status', [401, 403, 507])
def test_auth_permission_quota_fail_without_escalation(status):
    drive, session = backend([Response(status)])
    with pytest.raises(GraphError, match=str(status)):
        drive._root()
    assert len(session.calls) == 1


def test_no_connection_without_authorization():
    drive = OneDriveBackend(lambda: pytest.fail('token requested'))
    with pytest.raises(GraphError):
        drive._root()


def test_retry_and_signed_download_no_credentials():
    drive, session = backend([Response(doc={'id':'root'}), Response(429, headers={'Retry-After':'0'}),
                              Response(503), Response(302, headers={'Location':'https://download.example/file'}),
                              Response(content=b'exact')])
    assert drive.get('objects/ab/hash.jpg') == b'exact'
    assert 'Authorization' not in session.calls[-1][2]['headers']
    assert session.calls[-1][2]['allow_redirects'] is False


def test_retry_transport_and_exhaustion():
    drive, session = backend([requests.ConnectionError('secret')] * 4)
    with pytest.raises(GraphError) as exc:
        drive._root()
    assert 'secret' not in str(exc.value)
    assert len(session.calls) == 4


def test_chunk_upload_verified_and_no_bearer_on_upload_url():
    data = b'x' * (CHUNK + 10)
    responses = [Response(doc={'id':'root'}), Response(404)]
    responses += [Response(201, {'id':str(i), 'folder':{}}) for i in range(3)]
    responses += [Response(doc={'uploadUrl':'https://upload.example/session'}),
                  Response(202, {'nextExpectedRanges':[f'{CHUNK}-']}), Response(201), Response(content=data)]
    drive, session = backend(responses)
    drive.put('objects/ab/hash.jpg', data)
    chunks = [c for c in session.calls if c[0] == 'PUT']
    assert len(chunks) == 2
    assert b''.join(c[2]['data'] for c in chunks) == data
    assert all('Authorization' not in c[2]['headers'] for c in chunks)
    create = [c for c in session.calls if c[1].endswith('createUploadSession')][0]
    assert create[2]['json']['item']['@microsoft.graph.conflictBehavior'] == 'fail'


def test_existing_remote_must_match():
    # same size, different bytes: compared byte for byte, never overwritten
    drive, session = backend([Response(doc={'id':'root'}), Response(doc={'file': {}, 'size': 5}),
                              Response(content=b'wrong')])
    with pytest.raises(IntegrityError):
        drive.put('objects/a', b'right')
    assert all(c[0] == 'GET' for c in session.calls)


def test_existing_different_size_conflicts_without_download():
    drive, session = backend([Response(doc={'id':'root'}), Response(doc={'file': {}, 'size': 99})])
    with pytest.raises(IntegrityError):
        drive.put('objects/a', b'right')
    assert len(session.calls) == 2 and not any(c[1].endswith(':/content') for c in session.calls)


def test_expired_access_token_is_renewed_once():
    tokens = []
    def provider(force=False):
        tokens.append(force)
        return 'renewed' if force else 'expired'
    provider.supports_force_refresh = True
    session = Session([Response(401), Response(doc={'id': 'root'})])
    drive = OneDriveBackend(provider, authorized=True, session=session, sleep=lambda _: None)
    drive._root()
    assert tokens == [False, True]
    assert session.calls[-1][2]['headers']['Authorization'] == 'Bearer renewed'


def test_revoked_consent_is_not_retried_forever():
    def provider(force=False):
        return 'x'
    provider.supports_force_refresh = True
    session = Session([Response(401), Response(401)])
    drive = OneDriveBackend(provider, authorized=True, session=session, sleep=lambda _: None)
    with pytest.raises(GraphError, match='401'):
        drive._root()
    assert len(session.calls) == 2


def test_existing_identical_is_incremental():
    drive, session = backend([Response(doc={'id':'root'}), Response(doc={'file': {}, 'size': 5}),
                              Response(content=b'exact')])
    drive.put('objects/a', b'exact')
    assert len(session.calls) == 3


def test_pagination_rejects_credential_exfiltration():
    drive, session = backend([Response(doc={'id':'root'}), Response(doc={'value':[], '@odata.nextLink':'https://evil.test/'})])
    with pytest.raises(GraphError, match='credential forwarding'):
        drive.list_snapshots()


def test_synthetic_cleanup_only_owned_key(monkeypatch):
    drive, session = backend([Response(204)])
    drive.root_id = 'root'
    saved = {}
    monkeypatch.setattr(drive, 'put', lambda key, data: saved.update({key:data}))
    monkeypatch.setattr(drive, 'get', lambda key: saved[key])
    result = drive.connectivity_test()
    key = next(iter(saved))
    assert key.startswith('smoke/')
    assert result['synthetic'] is True
    assert session.calls[0][0] == 'DELETE'
    assert key in session.calls[0][1]


def test_cache_rotation_persisted_before_token_use():
    class Cache:
        has_state_changed = True
        def serialize(self): return 'rotated'
    class App:
        def get_accounts(self): return ['account']
        def acquire_token_silent(self, scopes, account, **kw):
            assert scopes == ['Files.ReadWrite.AppFolder']
            return {'access_token':'access'}
    saved = []
    assert token_provider(App(), Cache(), saved.append)() == 'access'
    assert saved == ['rotated']
    def failing(value): raise RuntimeError('secret store unavailable')
    with pytest.raises(RuntimeError):
        token_provider(App(), Cache(), failing)()


def test_external_cache_never_repo_and_restrictive_mode(tmp_path):
    repo = tmp_path / 'repo'
    with pytest.raises(ValueError):
        external_cache_writer(repo / 'token', repo)
    cache = tmp_path / 'private' / 'cache'
    external_cache_writer(cache, repo)('secret')
    assert cache.stat().st_mode & 0o777 == 0o600


def test_device_auth_displays_no_tokens():
    class App:
        def initiate_device_flow(self, scopes): return {'user_code':'ABCD', 'device_code':'hidden'}
        def acquire_token_by_device_flow(self, flow): return {'access_token':'hidden'}
        def get_accounts(self): return [{'username': 'owner@example.test'}]
    class Cache:
        def serialize(self): return 'hidden'
    output, saved = [], []
    device_login(App(), Cache(), saved.append, output.append)
    assert 'hidden' not in ''.join(output)
    assert saved == ['hidden']


def test_device_login_for_another_account_is_never_saved():
    from tools.image_archive.auth import account_fingerprint
    class App:
        def initiate_device_flow(self, scopes): return {'user_code': 'ABCD'}
        def acquire_token_by_device_flow(self, flow): return {'access_token': 'hidden'}
        def get_accounts(self): return [{'username': 'someone-else@example.test'}]
    class Cache:
        def serialize(self): return 'hidden'
    saved = []
    with pytest.raises(RuntimeError, match='pinned archive owner'):
        device_login(App(), Cache(), saved.append, lambda _: None,
                     expected_fingerprint=account_fingerprint('Owner@Example.test'))
    assert saved == []


def test_pinned_account_is_required_for_every_token():
    from tools.image_archive.auth import account_fingerprint
    class App:
        def get_accounts(self): return [{'username': 'intruder@example.test'}]
        def acquire_token_silent(self, *a, **kw): pytest.fail('token requested for an unpinned account')
    class Cache:
        has_state_changed = False
    acquire = token_provider(App(), Cache(), lambda _: None, account_fingerprint('owner@example.test'))
    with pytest.raises(RuntimeError, match='pinned'):
        acquire()


def test_interrupted_chunk_progress_is_resumed():
    data = b'x' * (CHUNK + 10)
    responses = [Response(doc={'id':'root'}), Response(404)]
    responses += [Response(201, {'id':str(i), 'folder':{}}) for i in range(3)]
    responses += [Response(doc={'uploadUrl':'https://upload.example/session'}),
                  Response(416), Response(doc={'nextExpectedRanges':[f'{CHUNK}-']}),
                  Response(201), Response(content=data)]
    drive, session = backend(responses)
    drive.put('objects/ab/hash.jpg', data)
    chunks = [c for c in session.calls if c[0] == 'PUT']
    assert chunks[-1][2]['data'] == b'x' * 10


def test_acknowledged_but_corrupt_upload_fails():
    responses = [Response(doc={'id':'root'}), Response(404)]
    responses += [Response(201, {'id':str(i), 'folder':{}}) for i in range(3)]
    responses += [Response(doc={'uploadUrl':'https://upload.example/session'}), Response(201), Response(content=b'wrong')]
    drive, _ = backend(responses)
    with pytest.raises(IntegrityError):
        drive.put('objects/ab/hash.jpg', b'original')


def test_conflicting_concurrent_upload_is_not_success():
    responses = [Response(doc={'id':'root'}), Response(404)]
    responses += [Response(201, {'id':str(i), 'folder':{}}) for i in range(3)]
    responses += [Response(409), Response(content=b'other')]
    drive, _ = backend(responses)
    with pytest.raises(IntegrityError):
        drive.put('objects/ab/hash.jpg', b'original')


def test_revoked_consent_requires_reauthorization():
    class App:
        def get_accounts(self): return ['account']
        def acquire_token_silent(self, *a, **kw): return {'error':'invalid_grant'}
    class Cache:
        has_state_changed = False
    with pytest.raises(RuntimeError, match='reauthorize'):
        token_provider(App(), Cache(), lambda _: None)()


def test_new_runner_loads_serialized_cache_and_consumer_authority():
    from tools.image_archive.auth import client
    class Cache:
        def deserialize(self, value): self.value = value
    class MSAL:
        SerializableTokenCache = Cache
        @staticmethod
        def PublicClientApplication(client_id, authority, token_cache):
            assert client_id == 'id'
            assert authority.endswith('/consumers')
            assert token_cache.value == 'previous-run-cache'
            return 'app'
    app, cache = client('id', 'previous-run-cache', msal_module=MSAL)
    assert app == 'app'


def test_encrypted_github_cache_writer_never_command_line_secret(monkeypatch):
    import subprocess
    from tools.image_archive.auth import github_cache_writer
    calls=[]
    def run(cmd, **kw):
        calls.append((cmd,kw))
        return type('Result', (), {'returncode':0})()
    monkeypatch.setattr(subprocess,'run',run)
    github_cache_writer('Biggregw/watch-align')('rotated-secret')
    assert 'rotated-secret' not in repr(calls[0][0])
    assert calls[0][1]['input'] == 'rotated-secret'
    assert calls[0][1]['capture_output'] is True
    # environment-scoped (protected by the onedrive-archive environment), never a repository-wide secret
    assert calls[0][0][calls[0][0].index('--env') + 1] == 'onedrive-archive'


def test_remote_object_listing_paginates_and_descends():
    drive,_=backend([Response(doc={'id':'root'}),
                     Response(doc={'value':[{'name':'ab','folder':{}}]}),
                     Response(doc={'value':[{'name':'one.jpg','file':{}}], '@odata.nextLink':GRAPH+'/next'}),
                     Response(doc={'value':[{'name':'two.jpg','file':{}}]})])
    assert drive.list_objects()==['objects/ab/one.jpg','objects/ab/two.jpg']


def test_failed_cache_save_remains_pending_after_msal_clears_dirty_flag():
    class Cache:
        has_state_changed=True
        def serialize(self):
            self.has_state_changed=False
            return 'rotated'
    class App:
        def get_accounts(self): return ['account']
        def acquire_token_silent(self,*a,**kw): return {'access_token':'access'}
    attempts=[]
    def save(value):
        attempts.append(value)
        if len(attempts)<3: raise RuntimeError('store unavailable')
    acquire=token_provider(App(),Cache(),save)
    for _ in range(2):
        with pytest.raises(RuntimeError): acquire()
    assert acquire()=='access'
    assert attempts==['rotated']*3


def test_long_retry_after_defers_instead_of_hammering():
    drive,session=backend([Response(429,headers={'Retry-After':'600'})])
    with pytest.raises(GraphError,match='retry later'):
        drive._root()
    assert len(session.calls)==1
