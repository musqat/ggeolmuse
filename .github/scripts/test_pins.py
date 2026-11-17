"""외부 액션은 커밋 SHA, Dockerfile 베이스 이미지는 digest 로 고정됐는지(사용: python3 -m unittest discover -s .github/scripts)"""
import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
USES = re.compile(r"^\s*-?\s*uses:\s*(\S+)", re.M)
FROM = re.compile(r"^FROM\s+(\S+)", re.M)


def tracked(pattern):
    out = subprocess.run(["git", "ls-files", pattern], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    return [ROOT / line for line in out.splitlines() if line]


class PinTest(unittest.TestCase):

    def test_external_actions_pinned_to_commit(self):
        loose = []
        for path in tracked(".github/*.yml"):
            for ref in USES.findall(path.read_text(encoding="utf-8")):
                if ref.startswith("./") or ref.startswith("docker://"):
                    continue
                if not re.fullmatch(r"[\w.-]+/[\w./-]+@[0-9a-f]{40}", ref):
                    loose.append(f"{path.relative_to(ROOT)}: {ref}")
        self.assertEqual(loose, [], "커밋 SHA 로 고정 안 된 액션")

    def test_base_images_pinned_to_digest(self):
        loose = []
        for path in tracked("*Dockerfile*"):
            for image in FROM.findall(path.read_text(encoding="utf-8")):
                if "@sha256:" not in image:
                    loose.append(f"{path.relative_to(ROOT)}: {image}")
        self.assertEqual(loose, [], "digest 로 고정 안 된 베이스 이미지")


if __name__ == "__main__":
    unittest.main()
