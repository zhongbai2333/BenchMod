#!/usr/bin/env python3
"""Certify an OpenGL -> Vulkan graphics-migration/1 report pair, using stdlib only.

Usage: python3 tools/graphics_migration_compare.py BASELINE CANDIDATE \
    --output comparison.json [--diff-dir diffs]

Inputs are graphics-migration.json sidecars, normally in result artifacts/custom.
Their raw artifacts are tightly packed RGBA8 files adjacent to the sidecar,
compared in recorded row order without backend-specific orientation correction.
No image codec, graphics driver, or third-party Python package is needed.
Exit 0 means PASS; 1 means a comparison failed or evidence is inconclusive;
2 means a CLI/output error. Driver strings may differ between APIs. Minecraft,
device, scene identities, and expected bytes must match exactly. Vendor matching
uses only explicit NVIDIA/AMD/Intel aliases; raw and normalized names are reported.
Unknown vendor strings require exact equality. Different driver-renderer device names remain
INCONCLUSIVE, even on the same hardware: use verified same-hardware reports with
matching device identities; there is no blanket device-name override.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
from typing import Any

SCHEMA_VERSION = "graphics-migration/1"
SUITE_ID = "graphics-migration"
SUITE_REVISION = 1
SCENARIO_ID = "graphics-migration.suite"
SEED = 602263
# width, height, per-channel tolerance. One out-of-tolerance pixel is a failure.
SCENES = {
    "rgba-stride-orientation": (17, 9, 0),
    "rg8-uv-channels": (17, 9, 2),
    "buffer-reuse": (8, 8, 0),
    "shader-depth-blend": (8, 8, 2),
    "offscreen-copy": (17, 9, 0),
    "resource-recreate": (17, 9, 0),
}
OPTIONAL_SCENE = "engine-resource-reload"
MAX_REPORT_BYTES = 1024 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
ARTIFACT = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*\.rgba\Z")
ENVIRONMENT_FIELDS = ("minecraft", "requestedBackend", "actualBackend", "deviceName", "vendor", "driver")


class InvalidReport(ValueError):
    """A sidecar or its evidence does not satisfy the v1 contract."""

    def __init__(self, errors: list[str]):
        super().__init__("; ".join(errors))
        self.errors = errors


def normalize_backend(value: str) -> str | None:
    """Normalize backend names, never guess an API from a renderer/device string."""
    name = re.sub(r"[\s_-]", "", value).casefold()
    if name in ("opengl", "gl", "openglbackend", "glbackend"):
        return "opengl"
    if name in ("vulkan", "vk", "vulkanbackend", "vkbackend"):
        return "vulkan"
    return None


def normalize_vendor(value: str) -> str | None:
    """Use complete known aliases, never substrings or inferred hardware vendors."""
    # Observed GL aliases (including Intel/Intel Inc./Intel Corporation):
    # https://feedback.wildfiregames.com/report/opengl/feature/GL_VENDOR
    # Intel Inc. also appears in the official GameMaker os_get_info reference.
    # Keep this list explicit: Mesa, ANGLE, and unknown labels are not GPU IDs.
    aliases = {
        "nvidia": "NVIDIA", "nvidia corporation": "NVIDIA",
        "intel": "INTEL", "intel inc.": "INTEL", "intel corporation": "INTEL",
        "amd": "AMD", "ati technologies inc.": "AMD", "advanced micro devices, inc.": "AMD",
    }
    return aliases.get(" ".join(value.split()).casefold())


def _integer(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _number(value: Any) -> bool:
    return _integer(value) or (isinstance(value, float) and math.isfinite(value))


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON property {key!r}")
        result[key] = value
    return result


def _nonfinite(value: str) -> None:
    raise ValueError(f"non-finite JSON number {value}")


def _finite_float(value: str) -> float:
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(f"non-finite JSON number {value}")
    return number


def _read_bounded(path: Path, limit: int) -> bytes:
    # Refuse pipes/devices before opening. Bounded reads also catch a growing file.
    if not stat.S_ISREG(path.stat().st_mode):
        raise ValueError("not a regular file")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError(f"file exceeds {limit} bytes")
    return data


def _validate_shape(report: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(report, dict):
        return ["report must be an object"]
    for key, expected in (("schemaVersion", SCHEMA_VERSION), ("suiteId", SUITE_ID),
                          ("suiteRevision", SUITE_REVISION),
                          ("scenarioId", SCENARIO_ID)):
        if type(report.get(key)) is not type(expected) or report.get(key) != expected:
            errors.append(f"{key} must equal {expected!r}")
    if not _integer(report.get("seed")):
        errors.append("seed must be an integer")
    if "environmentValid" in report and not isinstance(report["environmentValid"], bool):
        errors.append("environmentValid must be a boolean")
    if "invalidations" in report and (not isinstance(report["invalidations"], list) or
            any(not isinstance(reason, str) for reason in report["invalidations"])):
        errors.append("invalidations must be a string array")
    if report.get("status") not in ("PASS", "FAIL", "BLOCKED"):
        errors.append("report status must be PASS, FAIL, or BLOCKED")
    environment = report.get("environment")
    if not isinstance(environment, dict):
        errors.append("environment must be an object")
    else:
        for field in ENVIRONMENT_FIELDS:
            value = environment.get(field)
            if not isinstance(value, str):
                errors.append(f"environment.{field} must be a string")
            elif report.get("status") == "PASS" and not value.strip():
                errors.append(f"environment.{field} must be nonempty for PASS")
        for field in ("requestedBackend", "actualBackend"):
            value = environment.get(field)
            if isinstance(value, str) and value and normalize_backend(value) is None:
                errors.append(f"environment.{field} has an unrecognized backend {value!r}")
    scenes = report.get("scenes")
    if not isinstance(scenes, list):
        errors.append("scenes must be an array")
        return errors
    if not 6 <= len(scenes) <= 7:
        errors.append("scenes must contain the six required scenes and at most one optional scene")
    seen: set[str] = set()
    for index, scene in enumerate(scenes):
        prefix = f"scenes[{index}]"
        if not isinstance(scene, dict):
            errors.append(f"{prefix} must be an object")
            continue
        scene_id = scene.get("id")
        if not isinstance(scene_id, str) or scene_id not in (*SCENES, OPTIONAL_SCENE):
            errors.append(f"{prefix}.id is not a recognized v1 scene")
            continue
        prefix = scene_id
        if scene_id in seen:
            errors.append(f"duplicate scene id {scene_id!r}")
        seen.add(scene_id)
        optional = scene_id == OPTIONAL_SCENE
        width, height, tolerance = (0, 0, 0) if optional else SCENES[scene_id]
        for key, expected in (("revision", 1), ("width", width), ("height", height),
                              ("channels", 4), ("tolerancePerChannel", tolerance)):
            if not _integer(scene.get(key)) or scene.get(key) != expected:
                errors.append(f"{prefix}.{key} must equal {expected}")
        ratio = scene.get("maxBadPixelRatio")
        if not _number(ratio) or ratio != 0:
            errors.append(f"{prefix}.maxBadPixelRatio must equal 0")
        status = scene.get("status")
        if optional and status != "SKIP":
            errors.append(f"{prefix} is an optional SKIP-only placeholder in suite revision 1")
        elif not optional and status not in ("PASS", "FAIL", "BLOCKED"):
            errors.append(f"{prefix}.status must be PASS, FAIL, or BLOCKED (required scenes cannot SKIP)")
        reason = scene.get("reason")
        if not isinstance(reason, str) or (status != "PASS" and not reason.strip()):
            errors.append(f"{prefix}.reason must be a string, nonempty for non-PASS scenes")
        if report.get("status") == "PASS" and not optional and status != "PASS":
            errors.append(f"PASS report contains non-PASS required scene {prefix}")
        checks = scene.get("checks")
        if not isinstance(checks, dict) or any(not isinstance(v, str) for v in checks.values()):
            errors.append(f"{prefix}.checks must be a string map")
        metrics = scene.get("metrics")
        if not isinstance(metrics, dict) or any(not _number(v) for v in metrics.values()):
            errors.append(f"{prefix}.metrics must contain finite numbers only")
        for kind in ("expected", "actual"):
            digest, artifact = scene.get(f"{kind}Sha256"), scene.get(f"{kind}Artifact")
            if not isinstance(digest, str) or (digest != "" and not SHA256.fullmatch(digest)):
                errors.append(f"{prefix}.{kind}Sha256 must be lowercase SHA-256 or empty")
            if not isinstance(artifact, str) or (artifact != "" and
                    (len(artifact) > 255 or not ARTIFACT.fullmatch(artifact))):
                errors.append(f"{prefix}.{kind}Artifact must be an adjacent relative .rgba filename or empty")
            if bool(digest) != bool(artifact):
                errors.append(f"{prefix}.{kind} hash and artifact must both be present or both be empty")
            if status == "PASS" and (not digest or not artifact):
                errors.append(f"{prefix}.{kind} hash and artifact are required for PASS")
            if optional and (digest or artifact):
                errors.append(f"{prefix} SKIP placeholder must have empty artifact and hash fields")
    for scene_id in SCENES.keys() - seen:
        errors.append(f"missing required scene {scene_id!r}")
    return errors


def load_report(path: Path | str) -> dict[str, Any]:
    """Fully validate metadata, safe artifact paths, raw lengths, and SHA-256."""
    path = Path(path)
    try:
        report = json.loads(_read_bounded(path, MAX_REPORT_BYTES),
                            object_pairs_hook=_unique_object, parse_constant=_nonfinite, parse_float=_finite_float)
    except (OSError, ValueError, RecursionError) as error:
        raise InvalidReport([f"cannot read report: {error}"]) from error
    errors = _validate_shape(report)
    if errors:
        raise InvalidReport(errors)
    root = path.resolve().parent
    pixels: dict[str, dict[str, bytes]] = {}
    for scene in report["scenes"]:
        scene_id = scene["id"]
        pixels[scene_id] = {}
        length = scene["width"] * scene["height"] * scene["channels"]
        for kind in ("expected", "actual"):
            name = scene[f"{kind}Artifact"]
            if not name:
                continue
            try:
                artifact_path = (root / name).resolve(strict=True)
                if artifact_path.parent != root:
                    raise ValueError("artifact escapes report directory (including through a symlink)")
                data = _read_bounded(artifact_path, length)
                if len(data) != length:
                    raise ValueError(f"raw RGBA size is {len(data)}, expected {length}")
                if hashlib.sha256(data).hexdigest() != scene[f"{kind}Sha256"]:
                    raise ValueError("SHA-256 does not match recorded hash")
                pixels[scene_id][kind] = data
            except (OSError, ValueError, RuntimeError) as error:
                errors.append(f"{scene_id}.{kind}Artifact: {error}")
    if errors:
        raise InvalidReport(errors)
    return {"path": str(path), "report": report,
            "scenes": {scene["id"]: scene for scene in report["scenes"]}, "pixels": pixels}


def pixel_difference(expected: bytes, actual: bytes, tolerance: int) -> dict[str, Any]:
    """Count a pixel as bad if any RGBA channel exceeds the inclusive tolerance."""
    if len(expected) != len(actual) or not expected or len(expected) % 4:
        raise ValueError("pixel buffers must be equal-length, nonempty RGBA8")
    deltas = [abs(left - right) for left, right in zip(expected, actual)]
    bad_pixels = sum(max(deltas[index:index + 4]) > tolerance for index in range(0, len(deltas), 4))
    total_pixels = len(expected) // 4
    return {"status": "PASS" if bad_pixels == 0 else "FAIL", "totalPixels": total_pixels,
            "badPixels": bad_pixels, "badPixelRatio": bad_pixels / total_pixels,
            "maxChannelDelta": max(deltas), "differentChannels": sum(d > 0 for d in deltas),
            "outOfToleranceChannels": sum(d > tolerance for d in deltas),
            "tolerancePerChannel": tolerance, "maxBadPixelRatio": 0}


def _atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=f".{path.name}.", delete=False) as stream:
            temporary = stream.name
            stream.write(data)
        os.replace(temporary, path)
    finally:
        if temporary is not None and os.path.exists(temporary):
            os.unlink(temporary)


def _write_diff(path: Path, width: int, height: int, left: bytes, right: bytes) -> None:
    # Grayscale max RGBA delta makes alpha-only errors visible in an RGB PPM.
    rgb = bytearray()
    for offset in range(0, len(left), 4):
        delta = max(abs(left[offset + c] - right[offset + c]) for c in range(4))
        rgb.extend((delta, delta, delta))
    _atomic_write(path, f"P6\n{width} {height}\n255\n".encode("ascii") + rgb)


def compare_reports(baseline_path: Path | str, candidate_path: Path | str,
                    diff_dir: Path | str | None = None) -> dict[str, Any]:
    """Return a JSON-serializable verdict; invalid or incomplete evidence never passes."""
    result: dict[str, Any] = {
        "schemaVersion": "graphics-migration-comparison/1", "status": "INCONCLUSIVE",
        "policy": {"suiteId": SUITE_ID, "suiteRevision": SUITE_REVISION, "seedMustMatch": True,
                   "baselineBackend": "opengl", "candidateBackend": "vulkan",
                   "expectedPixelsMustMatch": True, "maxBadPixelRatio": 0,
                   "vendorMatching": "explicit-known-aliases-or-exact", "deviceMatching": "exact"},
        "issues": [], "scenes": [],
    }
    loaded = {}
    for label, path in (("baseline", baseline_path), ("candidate", candidate_path)):
        result[label] = {"report": str(path)}
        try:
            loaded[label] = load_report(path)
        except InvalidReport as error:
            result["issues"].extend({"code": "INVALID_REPORT", "report": label, "message": message}
                                    for message in error.errors)
            continue
        report = loaded[label]["report"]
        result[label].update({"status": report["status"], "environment": report["environment"],
                              "normalizedBackend": normalize_backend(report["environment"]["actualBackend"]),
                              "normalizedVendor": normalize_vendor(report["environment"]["vendor"]),
                              "environmentValid": report.get("environmentValid"),
                              "invalidations": report.get("invalidations", [])})
    if len(loaded) != 2:
        return result
    baseline, candidate = loaded["baseline"], loaded["candidate"]

    def issue(code: str, message: str) -> None:
        result["issues"].append({"code": code, "message": message})

    if baseline["report"]["seed"] != candidate["report"]["seed"]:
        issue("SEED_MISMATCH", f"seed differs: {baseline['report']['seed']} vs {candidate['report']['seed']}")
    else:
        result["policy"]["seed"] = baseline["report"]["seed"]
    for label, expected_backend in (("baseline", "opengl"), ("candidate", "vulkan")):
        report = loaded[label]["report"]
        if report.get("environmentValid") is False or report.get("invalidations"):
            issue("ENVIRONMENT_INVALID", f"{label} environment was invalidated: {report.get('invalidations', [])}")
        environment = loaded[label]["report"]["environment"]
        requested = normalize_backend(environment["requestedBackend"])
        actual = normalize_backend(environment["actualBackend"])
        if requested != expected_backend or actual != expected_backend:
            issue("BACKEND_MISMATCH", f"{label} requires requested and actual {expected_backend}; "
                  f"got requested {environment['requestedBackend']!r}, actual {environment['actualBackend']!r}")
    left_environment = baseline["report"]["environment"]
    right_environment = candidate["report"]["environment"]
    left_vendor = normalize_vendor(left_environment["vendor"])
    right_vendor = normalize_vendor(right_environment["vendor"])
    if (left_vendor is None or right_vendor is None) and left_environment["vendor"] != right_environment["vendor"]:
        issue("UNKNOWN_VENDOR", f"environment.vendor differs and cannot be normalized safely: "
              f"{left_environment['vendor']!r} vs {right_environment['vendor']!r}")
    elif left_vendor != right_vendor:
        issue("ENVIRONMENT_MISMATCH", f"environment.vendor differs after explicit alias normalization: "
              f"{left_vendor!r} vs {right_vendor!r}")
    for field in ("minecraft", "deviceName"):
        if left_environment[field] != right_environment[field]:
            issue("ENVIRONMENT_MISMATCH", f"environment.{field} differs: "
                  f"{left_environment[field]!r} vs {right_environment[field]!r}")
    result["driverChanged"] = left_environment["driver"] != right_environment["driver"]
    if baseline["scenes"].keys() != candidate["scenes"].keys():
        issue("SCENE_SET_MISMATCH", "baseline and candidate scene sets differ, including optional scenes")
    for scene_id in baseline["scenes"].keys() & candidate["scenes"].keys():
        left_pixels, right_pixels = baseline["pixels"][scene_id], candidate["pixels"][scene_id]
        if "expected" in left_pixels and "expected" in right_pixels and left_pixels["expected"] != right_pixels["expected"]:
            issue("EXPECTED_IDENTITY_MISMATCH", f"{scene_id}: expected pixels differ between reports")
    if result["issues"]:
        return result
    verdict = "PASS"
    for label in ("baseline", "candidate"):
        status = loaded[label]["report"]["status"]
        if status != "PASS":
            issue("REPORT_NOT_PASS", f"{label} reports {status}")
            if status == "BLOCKED" or verdict != "INCONCLUSIVE":
                verdict = "INCONCLUSIVE" if status == "BLOCKED" else "FAIL"
    for scene_id in sorted(baseline["scenes"]):
        left_scene, right_scene = baseline["scenes"][scene_id], candidate["scenes"][scene_id]
        scene_result: dict[str, Any] = {"id": scene_id, "revision": 1, "status": "PASS",
                                        "width": left_scene["width"], "height": left_scene["height"],
                                        "channels": 4, "comparisons": {}}
        result["scenes"].append(scene_result)
        if scene_id == OPTIONAL_SCENE:
            scene_result.update({"status": "SKIP", "reasons": {"baseline": left_scene["reason"],
                                                                "candidate": right_scene["reason"]}})
            continue
        if left_scene["status"] != "PASS" or right_scene["status"] != "PASS":
            scene_result.update({"status": "INCONCLUSIVE" if "BLOCKED" in
                                 (left_scene["status"], right_scene["status"]) else "FAIL",
                                 "reasons": {"baseline": left_scene["reason"], "candidate": right_scene["reason"]}})
            if scene_result["status"] == "INCONCLUSIVE" or verdict == "PASS":
                verdict = scene_result["status"]
            continue
        left, right = baseline["pixels"][scene_id], candidate["pixels"][scene_id]
        pairs = {"baselineExpected": (left["expected"], left["actual"]),
                 "candidateExpected": (right["expected"], right["actual"]),
                 "backendParity": (left["actual"], right["actual"])}
        for name, (expected, actual) in pairs.items():
            difference = pixel_difference(expected, actual, left_scene["tolerancePerChannel"])
            scene_result["comparisons"][name] = difference
            if difference["status"] == "FAIL":
                issue("PIXEL_MISMATCH", f"{scene_id}.{name}: {difference['badPixels']} of "
                      f"{difference['totalPixels']} pixels exceed tolerance {left_scene['tolerancePerChannel']}")
                scene_result["status"] = "FAIL"
                if verdict != "INCONCLUSIVE":
                    verdict = "FAIL"
                if diff_dir is not None:
                    diff_path = Path(diff_dir) / f"{scene_id}-{name}.ppm"
                    _write_diff(diff_path, left_scene["width"], left_scene["height"], expected, actual)
                    difference["diffArtifact"] = str(diff_path)
    result["status"] = verdict
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("baseline", type=Path, help="OpenGL graphics-migration.json sidecar")
    parser.add_argument("candidate", type=Path, help="Vulkan graphics-migration.json sidecar")
    parser.add_argument("--output", required=True, type=Path, help="detailed JSON comparison report")
    parser.add_argument("--diff-dir", type=Path, help="write failed comparisons as absolute RGBA-delta PPMs")
    args = parser.parse_args(argv)
    try:
        # Do not accidentally destroy the evidence when choosing an output path.
        if args.output.resolve() in (args.baseline.resolve(), args.candidate.resolve()) or args.output.suffix.lower() == ".rgba":
            parser.error("--output must not overwrite an input report or raw .rgba artifact")
        result = compare_reports(args.baseline, args.candidate, args.diff_dir)
        _atomic_write(args.output, (json.dumps(result, indent=2, allow_nan=False) + "\n").encode("utf-8"))
    except (OSError, ValueError) as error:
        print(f"graphics-migration: cannot write comparison: {error}", file=sys.stderr)
        return 2
    print(f"graphics-migration: {result['status']} ({len(result['issues'])} issues); {args.output}")
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
