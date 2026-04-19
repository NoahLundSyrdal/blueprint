from orders.service import OrderService


def test_reopen_returns_order_record() -> None:
    reopened = OrderService().reopen("A-42")

    assert reopened.status == "reopened"
