from fastapi import FastAPI
from app.models.inventory import InventoryItem

app = FastAPI()

class InventoryService:
    def create_item(self, name: str) -> InventoryItem:
        return InventoryItem(name=name)
