"""In-memory OneDrive app folder with the real backend's immutability rules and injectable failures."""
from tools.image_archive.archive import IntegrityError


class FakeDrive:
    def __init__(self):
        self.files = {}
        self.fail_put_after = None   # raise after this many successful object uploads (simulated interruption)
        self.corrupt = set()         # keys whose download returns damaged bytes
        self.puts = 0
        self.gets = []

    def put(self, key, data):
        current = self.files.get(key)
        if current is not None:
            if current != data:
                raise IntegrityError('immutable remote object conflict')
            return
        if self.fail_put_after is not None and self.puts >= self.fail_put_after:
            raise ConnectionError('simulated network loss during upload')
        self.files[key] = bytes(data)
        self.puts += 1

    def get(self, key):
        self.gets.append(key)
        if key not in self.files:
            raise FileNotFoundError(key)
        data = self.files[key]
        return data[:-1] + b'!' if key in self.corrupt else data

    def metadata(self, key):
        return {'file': {}, 'size': len(self.files[key])} if key in self.files else None

    def list_children(self, prefix):
        prefix = prefix.rstrip('/') + '/'
        seen = {}
        for key, data in self.files.items():
            if key.startswith(prefix):
                rest = key[len(prefix):].split('/')
                if len(rest) == 1:
                    seen[rest[0]] = (rest[0], True, len(data))
                else:
                    seen.setdefault(rest[0], (rest[0], False, 0))
        return sorted(seen.values())
