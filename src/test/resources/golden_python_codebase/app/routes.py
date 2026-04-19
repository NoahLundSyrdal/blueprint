from fastapi import APIRouter

from .models import Project, User
from .service import InviteService

router = APIRouter()


@router.post("/projects/{project_id}/invites")
def create_invite_endpoint(project_id: str, email: str, service: InviteService) -> dict:
    project = Project(id=project_id, name="Demo", owner=User(id="u1", email="owner@example.com"))
    invite = service.create_invite(project, email, project.owner)
    return {"invite_id": invite.id}
