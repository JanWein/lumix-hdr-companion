import argparse
import json
import time
from pathlib import Path
from .core import read_frame, group_frames, merge


def scan(folder, output, count=3, step=1., max_gap=8., mode="hdr", max_side=2400, align=True, stable_seconds=2.):
    frames = []
    for p in sorted(folder.iterdir()):
        if p.suffix.lower() not in [".jpg", ".jpeg"] or time.time() - p.stat().st_mtime < stable_seconds:
            continue
        try:
            frames.append(read_frame(p))
        except (OSError, ValueError) as error:
            print(f"Skipped: {error}")
    results = []
    for group in group_frames(frames, count, step, max_gap):
        try:
            result = merge([x.path for x in group], output, mode, max_side, align)
            results.append(result)
        except (ValueError, OSError) as error:
            print(f"Bracket skipped: {error}")
    return results


def main():
    p = argparse.ArgumentParser(description="Local HDR companion. Existing S9 JPEG brackets; no camera SDK dependency.")
    p.add_argument("command", choices=["merge", "scan", "watch", "gui"])
    p.add_argument("inputs", type=Path, nargs="*")
    p.add_argument("--output", type=Path, default=Path("hdr-output"))
    p.add_argument("--count", type=int, choices=[3, 5, 7], default=3)
    p.add_argument("--step", type=float, default=1.)
    p.add_argument("--max-gap", type=float, default=8.)
    p.add_argument("--mode", choices=["hdr", "fusion"], default="hdr")
    p.add_argument("--max-side", type=int, default=2400, help="0 keeps full resolution; default 2400")
    p.add_argument("--no-align", action="store_true")
    a = p.parse_args()
    if a.max_side < 0 or a.step <= 0 or a.max_gap <= 0:
        p.error("Invalid processing limits")
    if a.command == "gui":
        from .gui import run
        run()
        return
    if a.command == "merge":
        print(json.dumps(merge(a.inputs, a.output, a.mode, a.max_side, not a.no_align), indent=2))
        return
    if len(a.inputs) != 1 or not a.inputs[0].is_dir():
        p.error("scan/watch requires one input directory")
    if a.inputs[0].resolve() == a.output.resolve():
        p.error("Input and output directories must differ")
    reported = set()
    try:
        while True:
            results = scan(a.inputs[0], a.output, a.count, a.step, a.max_gap, a.mode, a.max_side, not a.no_align)
            for r in results:
                if r["jpeg"] not in reported:
                    print(r["jpeg"], flush=True)
                    reported.add(r["jpeg"])
            if a.command != "watch":
                break
            time.sleep(3)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
