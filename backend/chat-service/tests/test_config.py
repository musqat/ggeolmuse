import importlib
import warnings
from pathlib import Path

import app.config as config_module
from app.config import SECRETS_DIR, Settings, secrets_dir


def test_secrets_dir_is_the_chart_mount_path():
    assert SECRETS_DIR == Path("/etc/secrets")


def test_settings_read_the_mount_path_when_it_exists(monkeypatch):
    is_dir = Path.is_dir
    monkeypatch.setattr(Path, "is_dir", lambda self: self == SECRETS_DIR or is_dir(self))
    try:
        with warnings.catch_warnings():
            # 모듈의 settings 가 없는 폴더를 읽으며 내는 경고
            warnings.simplefilter("ignore")
            reloaded = importlib.reload(config_module)
        assert reloaded.Settings.model_config["secrets_dir"] == SECRETS_DIR
    finally:
        monkeypatch.undo()
        importlib.reload(config_module)


def test_secrets_dir_only_when_folder_exists(tmp_path):
    assert secrets_dir(tmp_path) == tmp_path
    assert secrets_dir(tmp_path / "missing") is None


def test_secret_files_fill_values_without_env(tmp_path, monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("REDIS_PASSWORD", raising=False)
    (tmp_path / "OPENAI_API_KEY").write_text("key-from-file")
    (tmp_path / "REDIS_PASSWORD").write_text("pass-from-file")

    settings = Settings(_secrets_dir=tmp_path, _env_file=None)

    assert settings.openai_api_key == "key-from-file"
    assert settings.redis_password == "pass-from-file"


def test_env_wins_over_secret_file(tmp_path, monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "key-from-env")
    (tmp_path / "OPENAI_API_KEY").write_text("key-from-file")

    settings = Settings(_secrets_dir=tmp_path, _env_file=None)

    assert settings.openai_api_key == "key-from-env"
