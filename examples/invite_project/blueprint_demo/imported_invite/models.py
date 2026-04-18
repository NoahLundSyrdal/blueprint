from dataclasses import dataclass
from datetime import datetime

@dataclass
class InvitePolicy:
    max_invites: int
    domain: str

@dataclass
class User:
    id: str
    email: str

@dataclass
class Project:
    id: str
    name: str
    owner: User
    invite_policy: InvitePolicy

@dataclass
class Invite:
    id: str
    email: str
    project: Project
    status: str
    created_at: datetime
    expires_at: datetime

@dataclass
class InviteAuditLog:
    actor_email: str
    action: str
    created_at: datetime
    invite: Invite
    reason: str
