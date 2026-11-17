"""CI · E2E · 릴리스 워크플로 연결 테스트(사용: python3 -m unittest discover -s .github/scripts)"""
import re
import unittest
from pathlib import Path

WORKFLOWS = Path(__file__).resolve().parents[1] / "workflows"


def text(name):
    return (WORKFLOWS / name).read_text(encoding="utf-8")


def job(workflow, name):
    """잡 하나의 줄(다음 잡 · 주석 머리 전까지)"""
    match = re.search(rf"^  {re.escape(name)}:\n(.*?)(?=^  [#\w-]|\Z)", workflow, re.M | re.S)
    if not match:
        raise AssertionError(f"잡 {name} 없음")
    return match.group(1)


class WorkflowsTest(unittest.TestCase):

    def test_e2e_is_a_ci_job_with_the_required_check_name(self):
        self.assertFalse((WORKFLOWS / "e2e.yml").exists())
        self.assertIn("    name: E2E\n", job(text("ci.yml"), "e2e"))

    def test_ci_calls_release_after_checks_and_e2e_on_push(self):
        release = job(text("ci.yml"), "release")
        self.assertIn("uses: ./.github/workflows/release.yml", release)
        self.assertIn("needs: [ci-result, e2e]", release)
        self.assertIn("github.event_name == 'push'", release)
        self.assertIn("secrets: inherit", release)

    def test_release_starts_only_from_ci_or_by_hand(self):
        on = re.search(r"^on:\n(.*?)(?=^\S)", text("release.yml"), re.M | re.S).group(1)
        triggers = re.findall(r"^  (\w+):", on, re.M)
        self.assertEqual(triggers, ["workflow_call", "workflow_dispatch"])


if __name__ == "__main__":
    unittest.main()
