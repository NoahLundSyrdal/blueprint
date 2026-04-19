from inventory.models import InventoryItem


class InventoryService:
    def reserve(self, item_id: str, quantity: int) -> InventoryItem:
        return InventoryItem(id=item_id, quantity=quantity, reserved_for="cart")
