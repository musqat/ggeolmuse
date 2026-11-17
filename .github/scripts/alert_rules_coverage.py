"""렌더한 알림 규칙마다 대응 문서 주소와 울리는 promtool 테스트가 있는지 보고 빠진 게 있으면 종료 코드 1(사용: python3 alert_rules_coverage.py <규칙 파일> <테스트 파일>)"""
import re
import sys
from pathlib import Path

ALERT = re.compile(r"^\s*- alert: (\S+)\s*$")
BLOCK_START = re.compile(r"^\s*- (alert|record|name): ")
RUNBOOK = re.compile(r"^\s*runbook_url: \"?([^\"\s]+)\"?\s*$")
ALERTNAME = re.compile(r"^\s*alertname: (\S+)\s*$")
EMPTY_EXPECT = re.compile(r"^\s*exp_alerts: \[\]\s*$")
EXPECT = re.compile(r"^\s*exp_alerts:")


def runbook_urls(rules_text):
    """알림 이름 → runbook_url(없으면 None), 규칙 파일 순서"""
    urls = {}
    current = None
    for line in rules_text.splitlines():
        if BLOCK_START.match(line):
            match = ALERT.match(line)
            current = match.group(1) if match else None
            if current:
                urls[current] = None
            continue
        match = RUNBOOK.match(line)
        if current and match:
            urls[current] = match.group(1)
    return urls


def fired_alerts(test_text):
    """exp_alerts 가 비지 않은 테스트가 하나라도 있는 알림 이름"""
    fired = set()
    lines = test_text.splitlines()
    for i, line in enumerate(lines):
        match = ALERTNAME.match(line)
        if not match:
            continue
        expect = next((later for later in lines[i + 1:] if EXPECT.match(later)), None)
        if expect is not None and not EMPTY_EXPECT.match(expect):
            fired.add(match.group(1))
    return fired


def problems(rules_text, test_text):
    found = []
    fired = fired_alerts(test_text)
    for name, url in runbook_urls(rules_text).items():
        if url is None:
            found.append(f"{name}: runbook_url 없음")
        elif not url.endswith("#" + name.lower()):
            found.append(f"{name}: runbook_url 이 #{name.lower()} 로 끝나지 않음({url})")
        if name not in fired:
            found.append(f"{name}: 울리는 promtool 테스트 없음")
    return found


def main(argv):
    rules = Path(argv[0]).read_text(encoding="utf-8")
    tests = Path(argv[1]).read_text(encoding="utf-8")
    found = problems(rules, tests)
    for line in found:
        print(f"::error::{line}")
    if found:
        return 1
    print(f"알림 {len(runbook_urls(rules))}개 모두 대응 문서 주소 · 테스트 있음")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
