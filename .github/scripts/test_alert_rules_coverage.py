"""alert_rules_coverage.py 테스트(규칙 · 테스트 파일 견본, 사용: python3 -m unittest discover -s .github/scripts)"""
import unittest

from alert_rules_coverage import problems

RULES = """
groups:
  - name: availability
    rules:
      - alert: ServiceDown
        expr: up == 0
        annotations:
          summary: "다운"
          runbook_url: "https://example.test/RUNBOOK.md#servicedown"
      - record: slo:errors:ratio_rate5m
        expr: vector(0)
      - alert: TradeBuysPending
        expr: vector(1)
        annotations:
          runbook_url: "https://example.test/RUNBOOK.md#tradebuyspending"
"""

TESTS = """
tests:
  - interval: 1m
    alert_rule_test:
      - eval_time: 5m
        alertname: ServiceDown
        exp_alerts:
          - exp_labels:
              severity: critical
      - eval_time: 5m
        alertname: TradeBuysPending
        exp_alerts:
          - exp_labels:
              severity: warning
"""


class AlertRulesCoverageTest(unittest.TestCase):

    def test_every_alert_with_runbook_and_firing_test_passes(self):
        self.assertEqual(problems(RULES, TESTS), [])

    def test_alert_without_runbook_url(self):
        rules = RULES.replace('          runbook_url: "https://example.test/RUNBOOK.md#servicedown"\n', "")
        self.assertEqual(problems(rules, TESTS), ["ServiceDown: runbook_url 없음"])

    def test_runbook_anchor_must_be_lowercase_alert_name(self):
        rules = RULES.replace("#tradebuyspending", "#servicedown")
        self.assertEqual(problems(rules, TESTS), [
            "TradeBuysPending: runbook_url 이 #tradebuyspending 로 끝나지 않음(https://example.test/RUNBOOK.md#servicedown)"])

    def test_only_empty_expectation_is_not_a_firing_test(self):
        tests = TESTS.replace(
            "        alertname: TradeBuysPending\n        exp_alerts:\n          - exp_labels:\n              severity: warning\n",
            "        alertname: TradeBuysPending\n        exp_alerts: []\n")
        self.assertEqual(problems(RULES, tests), ["TradeBuysPending: 울리는 promtool 테스트 없음"])

    def test_record_rule_is_not_checked(self):
        rules = RULES.replace("expr: vector(0)", "expr: vector(0)\n        labels:\n          team: none")
        self.assertEqual(problems(rules, TESTS), [])


if __name__ == "__main__":
    unittest.main()
