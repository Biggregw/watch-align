"""Progress reporting inside an isolated comparison worker."""
import json
from pathlib import Path

_folder: Path | None = None


def write_json(path: Path, value) -> None:
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value), encoding='utf-8')
    temporary.replace(path)


def report(message: str) -> None:
    if _folder is not None:
        write_json(_folder / 'progress.json', {'message': message})
