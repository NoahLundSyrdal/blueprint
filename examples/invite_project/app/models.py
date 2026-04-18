from dataclasses import dataclass
from datetime import datetime


@dataclass
class User:
    id: str
    email: str

    def activate(self) -> None:
        pass


@dataclass
class Project:
    id: str
    name: str
    owner: User

    def invite_user(self, email: str) -> "Invite":
        return Invite(
            id="demo-invite",
            email=email,
            project=self,
            status="pending",
            created_at=datetime.utcnow(),
        )


@dataclass
class Invite:
    id: str
    email: str
    project: Project
    status: str
    created_at: datetime

    def accept(self) -> None:
        self.status = "accepted"
