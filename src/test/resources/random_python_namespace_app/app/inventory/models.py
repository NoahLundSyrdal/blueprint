from pydantic import BaseModel


class InventoryItem(BaseModel):
    id: str
    quantity: int
    reserved_for: str | None = None
