from app.models import Project, User
from app.repository import InviteRepository
from app.service import InviteService


def test_create_invite_flow():
    service = InviteService(InviteRepository())
    owner = User(id="u1", email="owner@example.com")
    project = Project(id="p1", name="Demo", owner=owner)

    invite = service.create_invite(project, "guest@example.com", owner)

    assert invite.project is project
