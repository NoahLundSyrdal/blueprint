"""Sales orders and line items that tie customers to inventory."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum

from app.models.customer import Customer
from app.models.dealership import InventoryVehicle
from app.models.payment import PaymentRecord


class OrderStatus(Enum):
    DRAFT = "draft"
    QUOTED = "quoted"
    AWAITING_PAYMENT = "awaiting_payment"
    PAID = "paid"
    DELIVERED = "delivered"
    CANCELLED = "cancelled"


@dataclass
class LineItem:
    """One InventoryVehicle sold as part of an order, at a negotiated price."""

    inventory_vehicle: InventoryVehicle
    sale_price: float
    warranty_months: int = 0


@dataclass
class SalesOrder:
    """A sales order connecting a Customer to one or more InventoryVehicles."""

    id: str
    customer: Customer
    dealership_id: str
    items: list[LineItem] = field(default_factory=list)
    payments: list[PaymentRecord] = field(default_factory=list)
    status: OrderStatus = OrderStatus.DRAFT
    created_at: datetime | None = None

    def subtotal(self) -> float:
        return sum(item.sale_price for item in self.items)

    def balance_due(self) -> float:
        captured = sum(
            p.amount
            for p in self.payments
            if p.status.value == "captured"
        )
        return max(0.0, self.subtotal() - captured)
