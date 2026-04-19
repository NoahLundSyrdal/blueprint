from inventory.service import InventoryService


def test_inventory_service_reserve_round_trip():
    item = InventoryService().reserve("sku-1", 4)

    assert item.quantity == 4
    assert item.reserved_for == "cart"
