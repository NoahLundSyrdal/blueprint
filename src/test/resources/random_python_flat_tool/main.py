from attrs import define


@define
class TaskConfig:
    retries: int = 3
    owner_email: str = "ops@example.com"


class TaskRunner:
    def __init__(self, config: TaskConfig):
        self.config = config

    def run(self, target: str) -> str:
        return f"Running {target} with {self.config.owner_email}"
