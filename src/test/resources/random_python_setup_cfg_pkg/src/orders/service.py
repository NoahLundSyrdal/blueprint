from orders.models import OrderRecord


class OrderService:
    def reopen(self, order_id: str) -> OrderRecord:
        return OrderRecord(order_id=order_id, status="reopened", total_cents=4200)
