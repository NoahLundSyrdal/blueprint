from main import TaskConfig, TaskRunner


def test_runner_uses_config_owner_email():
    config = TaskConfig(retries=2, owner_email="dev@example.com")

    result = TaskRunner(config).run("demo")

    assert "dev@example.com" in result
