"""trivy_gate.py 테스트(Trivy JSON 견본, 사용: python3 -m unittest discover -s .github/scripts)"""
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import trivy_gate


def vuln(pkg, vid, severity, fixed):
    return {"PkgName": pkg, "VulnerabilityID": vid, "Severity": severity, "InstalledVersion": "1.0",
            "FixedVersion": fixed}


class TrivyGateTest(unittest.TestCase):

    def run_gate(self, report):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "trivy.json"
            if report is not None:
                path.write_text(json.dumps(report), encoding="utf-8")
            out = io.StringIO()
            with redirect_stdout(out):
                code = trivy_gate.main([str(path)])
            return code, out.getvalue()

    def test_fixable_critical_fails_with_list(self):
        code, out = self.run_gate({"Results": [
            {"Target": "app", "Vulnerabilities": [vuln("libssl", "CVE-1", "CRITICAL", "1.1"),
                                                  vuln("jackson", "CVE-2", "HIGH", "2.0")]},
            {"Target": "os"}]})
        self.assertEqual(code, 1)
        self.assertIn("libssl CVE-1 1.0 → 1.1", out)
        self.assertNotIn("jackson", out)

    def test_unfixed_critical_and_high_pass(self):
        code, out = self.run_gate({"Results": [{"Target": "app", "Vulnerabilities": [
            vuln("libssl", "CVE-1", "CRITICAL", ""), vuln("jackson", "CVE-2", "HIGH", "2.0")]}]})
        self.assertEqual(code, 0)
        self.assertIn("고친 버전이 나온 CRITICAL 없음", out)

    def test_no_results_pass(self):
        code, out = self.run_gate({"Results": None})
        self.assertEqual(code, 0)
        self.assertIn("고친 버전이 나온 CRITICAL 없음", out)

    def test_same_vulnerability_in_two_targets_listed_once(self):
        v = vuln("libssl", "CVE-1", "CRITICAL", "1.1")
        code, out = self.run_gate({"Results": [{"Target": "a", "Vulnerabilities": [v]},
                                               {"Target": "b", "Vulnerabilities": [v]}]})
        self.assertEqual(code, 1)
        self.assertEqual(out.count("CVE-1"), 1)

    def test_missing_report_is_error(self):
        code, out = self.run_gate(None)
        self.assertEqual(code, 2)
        self.assertIn("리포트 없음", out)


if __name__ == "__main__":
    unittest.main()
