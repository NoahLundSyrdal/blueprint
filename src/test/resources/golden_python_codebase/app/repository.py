from .models import Invite, Project


class InviteRepository:
    def save(self, invite: Invite) -> Invite:
        return invite

    def find_for_project(self, project: Project) -> list[Invite]:
        return []
