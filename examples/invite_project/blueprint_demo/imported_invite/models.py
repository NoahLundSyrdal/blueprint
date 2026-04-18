from datetime import datetime

class Invite:
    def __init__(self, id: str, email: str, project: 'Project', status: str, created_at: datetime):
        self.id = id
        self.email = email
        self.project = project
        self.status = status
        self.created_at = created_at

    def accept(self):
        # Logic for accepting the invite
        pass

class Project:
    def __init__(self, id: str, name: str, owner: 'User'):
        self.id = id
        self.name = name
        self.owner = owner

    def invite_user(self):
        # Logic for inviting a user
        pass

class User:
    def __init__(self, id: str, email: str):
        self.id = id
        self.email = email

    def activate(self):
        # Logic to activate the user
        pass

class InvitePolicy:
    def __init__(self, id: str, name: str, description: str):
        self.id = id
        self.name = name
        self.description = description

    def check_invite(self):
        # Logic to check invite
        pass
# Relationships:
# Invite -> Project
# Project -> User
# InvitePolicy -> Invite
# InvitePolicy -> Project