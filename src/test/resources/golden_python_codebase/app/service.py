from .models import Invite, Project, User
from .repository import InviteRepository


class InviteService:
    def __init__(self, repo: InviteRepository):
        self.repo = repo

    def create_invite(self, project: Project, email: str, inviter: User) -> Invite:
        invite = Invite(
            id="invite-1",
            email=email,
            project=project,
            inviter=inviter,
            created_at="now",
        )
        return self.repo.save(invite)
