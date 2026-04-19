from dataclasses import dataclass
from datetime import datetime

@dataclass
class InvitePolicy:
    max_invites: int
    domain: str

@dataclass
@dataclass
class User:
    id: str
    email: str

@dataclass
@dataclass
class Project:
    id: str
    name: str
    owner: User
    invite_policy: InvitePolicy
@dataclass
class InviteReminder:
    send_at: datetime
    channel: str
@dataclass
class InviteEscalation:
    escalated_at: datetime
    reason: str
@dataclass
class Invite:
    id: str
    email: str
    project: Project
    status: str
    created_at: datetime
    expires_at: datetime
    accepted_at: datetime
    reminder: InviteReminder
    escalation: InviteEscalation
@dataclass
class InviteAuditLog:
    actor_email: str
    action: str
    created_at: datetime
    invite: Invite
    reason: str
    actor_ip: str
