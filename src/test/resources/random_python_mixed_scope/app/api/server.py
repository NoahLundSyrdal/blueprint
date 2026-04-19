from fastapi import FastAPI

from app.models import User

app = FastAPI()


@app.get("/users/{user_id}")
def get_user(user_id: str) -> User:
    return User(id=user_id, email="demo@example.com")
