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

    def test_e2e_follows_the_changes_pick(self):
        ci = text("ci.yml")
        e2e = job(ci, "e2e")
        self.assertIn("    needs: changes\n", e2e)
        self.assertIn("needs.changes.outputs.e2e != 'false'", e2e)
        self.assertIn("e2e: ${{ steps.pick.outputs.e2e }}", job(ci, "changes"))
        self.assertNotIn("e2e-changes:", ci)

    def test_ci_calls_release_after_checks_and_e2e_on_push(self):
        release = job(text("ci.yml"), "release")
        self.assertIn("uses: ./.github/workflows/release.yml", release)
        self.assertIn("needs: [ci-result, e2e, release-check]", release)
        self.assertIn("github.event_name == 'push'", release)
        self.assertIn("secrets: inherit", release)

    def test_release_only_when_the_merged_pr_checked_a_service(self):
        ci = text("ci.yml")
        check = job(ci, "release-check")
        self.assertIn("go run ./cmd/release-plan -checked", check)
        release = job(ci, "release")
        self.assertIn("needs.release-check.outputs.release == 'true'", release)
        self.assertIn("pr: ${{ needs.release-check.outputs.pr }}", release)
        self.assertIn("    inputs:\n      pr:", text("release.yml"))

    def test_pr_runs_only_read_caches(self):
        ci = text("ci.yml")
        self.assertNotRegex(ci, r"uses: actions/cache@")
        saves = re.findall(r"- name: [^\n]*\n(?:\s+id: [^\n]*\n)?\s+if: ([^\n]*)\n\s+uses: actions/cache/save@", ci)
        self.assertEqual(len(saves), len(re.findall(r"uses: actions/cache/restore@", ci)))
        self.assertTrue(all("github.event_name == 'push'" in cond for cond in saves))
        cache_to = re.findall(r"cache-to: ([^\n]*)", ci)
        self.assertTrue(cache_to and all("github.event_name == 'push'" in value for value in cache_to))

    def test_e2e_retries_stack_start(self):
        self.assertIn("docker compose up -d && exit 0", job(text("ci.yml"), "e2e"))

    def test_rollout_fails_without_token(self):
        rollout = job(text("release.yml"), "rollout")
        self.assertIn('echo "::error::환경 release 에 ARGOCD_TOKEN 없음', rollout)
        self.assertNotIn("확인 건너뜀", rollout)

    def test_release_starts_only_from_ci_or_by_hand(self):
        on = re.search(r"^on:\n(.*?)(?=^\S)", text("release.yml"), re.M | re.S).group(1)
        triggers = re.findall(r"^  (\w+):", on, re.M)
        self.assertEqual(triggers, ["workflow_call", "workflow_dispatch"])


if __name__ == "__main__":
    unittest.main()
