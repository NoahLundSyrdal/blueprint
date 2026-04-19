"""Sales order persistence — in-memory implementation."""

from __future__ import annotations

from typing import Optional

from app.models.sales import OrderStatus, SalesOrder


class OrderRepository:
    """CRUD for SalesOrder aggregates."""

    def __init__(self) -> None:
        self._orders: dict[str, SalesOrder] = {}

    def save(self, order: SalesOrder) -> SalesOrder:
        self._orders[order.id] = order
        return order

    def find(self, order_id: str) -> Optional[SalesOrder]:
        return self._orders.get(order_id)

    def by_customer(self, customer_id: str) -> list[SalesOrder]:
        return [o for o in self._orders.values() if o.customer.id == customer_id]

    def by_status(self, status: OrderStatus) -> list[SalesOrder]:
        return [o for o in self._orders.values() if o.status == status]
