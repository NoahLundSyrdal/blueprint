from pydantic import BaseModel

class InventoryItem(BaseModel):
    name: str
    quantity: int = 0
