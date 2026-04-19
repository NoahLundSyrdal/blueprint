from warehouse.service import WarehouseService


def test_create_shipment_returns_shipment() -> None:
    shipment = WarehouseService().create_shipment("abc", "DHL")

    assert shipment.carrier == "DHL"
