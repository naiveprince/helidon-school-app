#!/usr/bin/env python3
from __future__ import annotations

from datetime import datetime
import shutil
import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
import requests
from bs4 import BeautifulSoup

REQUIRED_FIELDS = [
    "schoolName",
    "category",
    "capacity",
    "examDates",
    "subjects",
    "alternateSubjects",
    "interview",
    "englishQualificationBenefit",
    "notes",
    "infoLink",
]


@dataclass(frozen=True)
class Config:
    input_path: Path
    output_path: Path
    start_no: int | None
    end_no: int | None
    pretty: bool


def parse_args() -> Config:
    parser = argparse.ArgumentParser(
        description="Update school JSON (scaffold for weekly automation)."
    )
    parser.add_argument(
        "--input",
        required=True,
        help="Path to the source JSON file.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to write the updated JSON file.",
    )
    parser.add_argument(
        "--start-no",
        type=int,
        default=None,
        help="1-based inclusive start index for targeted records.",
    )
    parser.add_argument(
        "--end-no",
        type=int,
        default=None,
        help="1-based inclusive end index for targeted records.",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="Write formatted JSON with indentation.",
    )
    args = parser.parse_args()

    if args.start_no is not None and args.start_no < 1:
        raise SystemExit("--start-no must be >= 1")
    if args.end_no is not None and args.end_no < 1:
        raise SystemExit("--end-no must be >= 1")
    if args.start_no is not None and args.end_no is not None and args.start_no > args.end_no:
        raise SystemExit("--start-no must be <= --end-no")

    return Config(
        input_path=Path(args.input),
        output_path=Path(args.output),
        start_no=args.start_no,
        end_no=args.end_no,
        pretty=args.pretty,
    )


def load_json(path: Path) -> list[dict[str, Any]]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"Input file not found: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON: {path} ({exc})")

    if not isinstance(data, list):
        raise SystemExit("Top-level JSON must be a list of school objects.")

    normalized: list[dict[str, Any]] = []
    for i, item in enumerate(data, start=1):
        if not isinstance(item, dict):
            raise SystemExit(f"Record #{i} is not an object.")
        normalized.append(item)
    return normalized


def validate_schema(records: list[dict[str, Any]]) -> None:
    for i, record in enumerate(records, start=1):
        missing = [key for key in REQUIRED_FIELDS if key not in record]
        if missing:
            raise SystemExit(f"Record #{i} is missing fields: {', '.join(missing)}")


def select_target_range(records: list[dict[str, Any]], start_no: int | None, end_no: int | None) -> list[dict[str, Any]]:
    if start_no is None and end_no is None:
        return records

    start_idx = 0 if start_no is None else start_no - 1
    end_idx = len(records) if end_no is None else end_no
    return records[start_idx:end_idx]


def normalize_record(record: dict[str, Any]) -> dict[str, Any]:
    # Keep structure stable; fill missing optional keys with null.
    out = dict(record)
    for key in REQUIRED_FIELDS:
        out.setdefault(key, None)
    return out

def check_school_url(record: dict[str, Any]) -> dict[str, Any]:
    school_name = record.get("schoolName", "(unknown)")
    url = record.get("infoLink")

    result: dict[str, Any] = {
        "schoolName": school_name,
        "url": url,
        "ok": False,
        "statusCode": None,
        "title": None,
        "errorType": None,
        "errorMessage": None,
    }

    if not url:
        result["errorType"] = "MISSING_URL"
        result["errorMessage"] = "infoLink is null or empty"
        print(f"[SKIP] {school_name}: no URL")
        return result

    try:
        response = requests.get(
            url,
            timeout=15,
            headers={"User-Agent": "Mozilla/5.0"},
        )
        content_type = response.headers.get("Content-Type", "")

        if "text/html" in content_type:
            if response.encoding is None or response.encoding.lower() == "iso-8859-1":
                response.encoding = response.apparent_encoding

        result["statusCode"] = response.status_code
        result["ok"] = response.status_code == 200

        print(f"[OK] {school_name}: {response.status_code}")

        if "text/html" in content_type:
            soup = BeautifulSoup(response.text, "html.parser")
            title = soup.title.string.strip() if soup.title and soup.title.string else "No title"
            result["title"] = title
            print(f"      Title: {title}")

    except requests.exceptions.SSLError as e:
        result["errorType"] = "SSL_ERROR"
        result["errorMessage"] = str(e)
        print(f"[SSL ERROR] {school_name}: {e}")

    except requests.exceptions.RequestException as e:
        result["errorType"] = "REQUEST_ERROR"
        result["errorMessage"] = str(e)
        print(f"[ERROR] {school_name}: {e}")

    except Exception as e:
        result["errorType"] = "UNKNOWN_ERROR"
        result["errorMessage"] = f"{type(e).__name__}: {e}"
        print(f"[UNKNOWN ERROR] {school_name}: {e}")

    return result

def main() -> int:
    cfg = parse_args()

    backup_json()

    records = load_json(cfg.input_path)
    validate_schema(records)

    target_records = select_target_range(records, cfg.start_no, cfg.end_no)

    report: list[dict[str, Any]] = []
    for record in target_records:
        report.append(check_school_url(record))

    failed = [r for r in report if not r["ok"]]
    print(f"\nSummary: {len(report) - len(failed)} ok, {len(failed)} failed")

    report_path = cfg.output_path.parent / "check-report.json"
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        )
    print(f"Wrote {report_path}")

    success_by_id: dict[int, dict] = {}
    now = datetime.now().isoformat(timespec="seconds")

    for record, result in zip(target_records, report):
        if result["ok"]:
            updated = dict(record)
            updated["lastCheckedAt"] = now
            success_by_id[record["id"]] = updated

    merged_records = merge_successful_updates(records, success_by_id)
    write_latest_json(cfg.output_path, merged_records)

    return 0

def backup_json():
    repo_root = Path(__file__).resolve().parents[1]

    source = repo_root / "data" / "schools-v2.json"

    archive_dir = repo_root / "archive"
    archive_dir.mkdir(exist_ok=True)

    today = datetime.now().strftime("%Y-%m-%d")

    backup = archive_dir / f"schools-v2-{today}.json"

    shutil.copy2(source, backup)

    print(f"[BACKUP] {backup}")

def merge_successful_updates(
        old_records: list[dict],
        success_by_id: dict[int, dict],
) -> list[dict]:
    merged: list[dict] = []

    for old in old_records:
        school_id = old.get("id")
        updated = success_by_id.get(school_id)

        if updated is not None:
            merged.append(updated)
        else:
            merged.append(old)

    return merged

def archive_previous_json(output_path: Path) -> Path | None:
    if not output_path.exists():
        return None

    archive_dir = Path("archive")
    archive_dir.mkdir(parents=True, exist_ok=True)

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    archive_path = archive_dir / f"{output_path.stem}-{stamp}{output_path.suffix}"
    shutil.copy2(output_path, archive_path)
    print(f"[ARCHIVE] {output_path} -> {archive_path}")
    return archive_path


def write_latest_json(output_path: Path, records: list[dict]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    new_text = json.dumps(records, ensure_ascii=False, indent=2) + "\n"
    old_text = output_path.read_text(encoding="utf-8") if output_path.exists() else ""

    if old_text != new_text:
        archive_previous_json(output_path)
        output_path.write_text(new_text, encoding="utf-8")
        print(f"[WRITE] {output_path}")
    else:
        print("[SKIP] JSON unchanged")

if __name__ == "__main__":
    raise SystemExit(main())
