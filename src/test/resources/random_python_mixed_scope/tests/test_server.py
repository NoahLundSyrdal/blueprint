from app.api.server import app


def test_app_exists() -> None:
    assert app is not None
