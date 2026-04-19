"""End-to-end sales flow: draft → charge → deliver, with cancel path."""

from __future__ import annotations

import pytest

from app.models.payment import PaymentMethod, PaymentStatus
from app.models.sales import OrderStatus
from app.repositories.customer_repository import CustomerRepository
from app.repositories.order_repository import OrderRepository
from app.repositories.vehicle_repository import VehicleRepository
from app.services.sales_service import SalesService


def test_happy_path_draft_charge_deliver(
    customers: CustomerRepository,
    vehicles: VehicleRepository,
    orders: OrderRepository,
    sales_service: SalesService,
) -> None:
    ada = customers.find("cust_ada")
    assert ada is not None

    order = sales_service.draft_order(ada, "d_oslo", ["A100", "B200"], warranty_months=12)
    assert order.status == OrderStatus.QUOTED
    assert len(order.items) == 2
    # Inventory should now show those two as reserved
    assert {v.stock_number for v in sales_service.inventory.available("d_oslo")} == {"C300"}

    order = sales_service.charge(order, PaymentMethod.CREDIT_CARD)
    assert order.status == OrderStatus.PAID
    assert order.payments[-1].status == PaymentStatus.CAPTURED
    assert order.balance_due() == 0.0

    order = sales_service.deliver(order)
    assert order.status == OrderStatus.DELIVERED

    # Notifications side-effects: confirmation + delivery
    sent = sales_service.notifications.sent  # type: ignore[attr-defined]
    assert len(sent) == 2
    assert sent[0][0] == ada.email
    assert "confirmed" in sent[0][1].lower()
    assert "delivery" in sent[1][1].lower()


def test_cancel_releases_inventory(
    customers: CustomerRepository,
    sales_service: SalesService,
) -> None:
    ada = customers.find("cust_ada")
    assert ada is not None
    order = sales_service.draft_order(ada, "d_oslo", ["A100"])
    assert {v.stock_number for v in sales_service.inventory.available("d_oslo")} == {"B200", "C300"}

    sales_service.cancel(order)
    assert order.status == OrderStatus.CANCELLED
    assert {v.stock_number for v in sales_service.inventory.available("d_oslo")} == {"A100", "B200", "C300"}


def test_deliver_before_paid_fails(
    customers: CustomerRepository,
    sales_service: SalesService,
) -> None:
    ada = customers.find("cust_ada")
    assert ada is not None
    order = sales_service.draft_order(ada, "d_oslo", ["A100"])
    with pytest.raises(ValueError):
        sales_service.deliver(order)


def test_charge_persists_via_order_repository(
    customers: CustomerRepository,
    orders: OrderRepository,
    sales_service: SalesService,
) -> None:
    ada = customers.find("cust_ada")
    assert ada is not None
    order = sales_service.draft_order(ada, "d_oslo", ["A100"])
    sales_service.charge(order, PaymentMethod.FINANCING)
    persisted = orders.find(order.id)
    assert persisted is not None
    assert persisted.status == OrderStatus.PAID
