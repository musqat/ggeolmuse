"""Trivy JSON 리포트에 고친 버전이 나온 CRITICAL 취약점이 있으면 목록을 보이고 종료 코드 1(사용: python3 trivy_gate.py <리포트>)"""
import json
import sys
from pathlib import Path


def fixable_criticals(report):
    """고친 버전이 있는 CRITICAL 의 (패키지, 취약점, 지금 버전, 고친 버전), 대상이 여럿이어도 한 번"""
    found = {}
    for result in report.get("Results") or []:
        for v in result.get("Vulnerabilities") or []:
            if v.get("Severity") == "CRITICAL" and v.get("FixedVersion"):
                key = (v.get("PkgName"), v.get("VulnerabilityID"))
                found[key] = (v.get("PkgName"), v.get("VulnerabilityID"), v.get("InstalledVersion"), v.get("FixedVersion"))
    return sorted(found.values())


def main(argv):
    path = Path(argv[0])
    if not path.is_file():
        print(f"리포트 없음: {path}")
        return 2
    rows = fixable_criticals(json.loads(path.read_text(encoding="utf-8")))
    if not rows:
        print("고친 버전이 나온 CRITICAL 없음")
        return 0
    print(f"고친 버전이 나온 CRITICAL {len(rows)}개(버전을 올리거나 .trivyignore 에 까닭과 함께):")
    for pkg, vid, installed, fixed in rows:
        print(f"  {pkg} {vid} {installed} → {fixed}")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
