from app.models import Project, User


def test_invite_user_creates_pending_invite():
    owner = User(id="user-1", email="owner@example.com")
    project = Project(id="project-1", name="Blueprint", owner=owner)

    invite = project.invite_user("friend@example.com")

    assert invite.project == project
    assert invite.email == "friend@example.com"
    assert invite.status == "pending"
