from dataclasses import dataclass
from datetime import datetime
from sqlalchemy.orm import Mapped, mapped_column, relationship


class Base:
    pass


@dataclass
class User:
    id: str
    email: str


@dataclass
class Project:
    id: str
    name: str
    owner: User


@dataclass
class Invite:
    id: str
    email: str
    project: Project
    inviter: User
    created_at: datetime


class ProjectRecord(Base):
    __tablename__ = "projects"

    id: Mapped[str] = mapped_column(primary_key=True)
    name: Mapped[str]
    invites: Mapped[list["InviteRecord"]] = relationship(back_populates="project")


class InviteRecord(Base):
    __tablename__ = "invites"

    id: Mapped[str] = mapped_column(primary_key=True)
    email: Mapped[str]
    project_id: Mapped[str]
    project: Mapped["ProjectRecord"] = relationship(back_populates="invites")
    audits: Mapped[list["AuditRecord"]] = relationship(back_populates="invite")


class AuditRecord(Base):
    __tablename__ = "invite_audits"

    id: Mapped[str] = mapped_column(primary_key=True)
    action: Mapped[str]
    invite_id: Mapped[str]
    invite: Mapped["InviteRecord"] = relationship(back_populates="audits")
