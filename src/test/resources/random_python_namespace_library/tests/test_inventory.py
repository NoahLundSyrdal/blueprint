from warehouse.domain.inventory import Warehouse


def test_warehouse_code() -> None:
    assert Warehouse("A1").code == "A1"
