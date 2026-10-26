"""coverage json 의 줄 · 분기 비율을 최소치와 견주고 표를 잡 요약에 남김

    python chat-coverage.py <coverage.json> <줄 최소치> <분기 최소치>
"""
import json
import os
import sys


def main(path: str, min_line: float, min_branch: float) -> int:
    with open(path, encoding="utf-8") as f:
        totals = json.load(f)["totals"]
    rows = [
        ("줄", 100 * totals["covered_lines"] / totals["num_statements"], min_line),
        ("분기", 100 * totals["covered_branches"] / totals["num_branches"], min_branch),
    ]
    table = "| 항목 | 값 | 최소치 |\n|---|---|---|\n" + "".join(
        f"| {name} | {value:.2f}% | {minimum:g}% |\n" for name, value, minimum in rows
    )
    print(table)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### chat 커버리지\n\n" + table)
    below = [name for name, value, minimum in rows if value < minimum]
    if below:
        print("최소치 미달: " + ", ".join(below))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], float(sys.argv[2]), float(sys.argv[3])))
