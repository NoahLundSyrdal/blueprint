from fastapi import APIRouter
from inventory.models import InventoryItem
from inventory.service import InventoryService

router = APIRouter()
service = InventoryService()


@router.post("/inventory/{item_id}")
def update_inventory(item_id: str, quantity: int) -> InventoryItem:
    return service.reserve(item_id, quantity)
