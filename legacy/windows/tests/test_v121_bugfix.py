from pathlib import Path

import v1_bugfix_v121


def test_v121_version_and_browser_fetch_contract():
    assert v1_bugfix_v121.V121_VERSION == "1.2.1"
    src = Path("v1_bugfix_v121.py").read_text(encoding="utf-8")
    assert "Chrome/128.0" in src
    assert "Official lookup failed" in src


def test_v121_removes_misleading_fixed_oval_and_stale_results():
    src = Path("v1_bugfix_v121.py").read_text(encoding="utf-8")
    assert "dashed oval" in src
    assert "guideWrap{position:relative;display:inline-block}" in src
    assert "URL.revokeObjectURL" in src
    assert "state.result=null" in src
