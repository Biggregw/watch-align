"""Submariner 12-series measured-only adapter v1.

The first implementation is intentionally bounded to the proven 124060 analyser. It emits the
generic long-form measurement contract, validates it, then materialises the existing wide eligible
values so the established calibration statistics can be regression-compared unchanged.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

import contracts
import measurement_contract
import production_measure

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
HARNESS = REPO / "tools" / "desktop-harness" / "run.sh"

ADAPTER_INFO = {
    "id": "submariner12_measured_v1",
    "version": "1",
    "reliability_policy": "sub124060_production_reliability_v1",
    "contract_schema_version": 1,
}


def run_harness(model: str, photos: list[tuple[str, Path]], out_csv: Path) -> None:
    model = contracts.exact_model(model, "measured-only adapter model")
    if model != "124060":
        raise contracts.ContractError(f"submariner12_measured_v1 currently supports only 124060, got {model}")
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    lst = out_csv.with_suffix(".list.tsv")
    lst.write_text("".join(f"{wid}\t{photo}\n" for wid, photo in photos), encoding="utf-8")
    subprocess.run(
        ["bash", str(HARNESS), "CalibMeasureContract", model, str(lst), str(out_csv)],
        cwd=REPO,
        check=True,
    )


def measure(config: dict, acq_root: Path, split_csv: Path, out_dir: Path,
            partition: str, cls: str) -> dict:
    model = contracts.exact_model(config.get("model"), "measured-only config model")
    family = str(config.get("family") or "").strip()
    if model != "124060" or family != "submariner_12":
        raise contracts.ContractError(
            f"submariner12_measured_v1 requires model 124060/family submariner_12, got {model}/{family}"
        )

    metrics = [str(m["app_key"]) for m in config["calibration_metrics"]]
    pref = out_dir / f"{model}_{partition}_{cls}"
    photos = production_measure.photo_list(acq_root, split_csv, partition, cls, model)
    contract_csv = Path(f"{pref}_measurement_contract.csv")
    wide_csv = Path(f"{pref}_photo.csv")

    if photos:
        run_harness(model, photos, contract_csv)
    else:
        measurement_contract.write_empty(contract_csv)

    contract_summary = measurement_contract.validate(contract_csv, config, ADAPTER_INFO, photos)
    measurement_contract.materialize_eligible_wide(contract_csv, config, photos, wide_csv)
    production_measure.validate_harness_output(wide_csv, model, photos)

    summary = production_measure.summarise(
        wide_csv,
        metrics,
        Path(f"{pref}_watch.csv"),
        Path(f"{pref}_repeatability.csv"),
        production_measure.source_map(split_csv),
    )
    summary["measurement_contract"] = contract_summary
    summary["measurement_adapter"] = dict(ADAPTER_INFO)
    return summary
