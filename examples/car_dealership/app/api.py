"""HTTP API — routes for the dealership app.

Blueprint classifies ``@router.get``/``@router.post`` handlers as ROUTE
components. Requires ``fastapi`` (declared as an optional dependency).
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.adapters.email_adapter import EmailNotificationAdapter
from app.adapters.stripe_adapter import StripePaymentAdapter
from app.models.payment import PaymentMethod
from app.repositories.customer_repository import CustomerRepository
from app.repositories.order_repository import OrderRepository
from app.repositories.vehicle_repository import VehicleRepository
from app.services.inventory_service import InventoryService
from app.services.pricing_service import PricingService
from app.services.sales_service import SalesService

router = APIRouter()

_customers = CustomerRepository()
_vehicles = VehicleRepository()
_orders = OrderRepository()
_sales = SalesService(
    orders=_orders,
    inventory=InventoryService(_vehicles),
    pricing=PricingService(),
    payment_gateway=StripePaymentAdapter(),
    notifications=EmailNotificationAdapter(),
)


@router.get("/dealerships/{dealership_id}/inventory")
def list_inventory(dealership_id: str) -> list[dict]:
    """List available vehicles at a dealership."""
    return [
        {
            "stock_number": v.stock_number,
            "description": v.vehicle.describe(),
            "price": v.asking_price,
        }
        for v in _sales.inventory.available(dealership_id)
    ]


@router.post("/orders")
def create_order(payload: dict) -> dict:
    """Draft a new order for a customer."""
    customer = _customers.find(payload["customer_id"])
    if customer is None:
        raise HTTPException(status_code=404, detail="customer not found")
    order = _sales.draft_order(
        customer=customer,
        dealership_id=payload["dealership_id"],
        stock_numbers=payload["stock_numbers"],
    )
    return {"order_id": order.id, "status": order.status.value}


@router.post("/orders/{order_id}/charge")
def charge_order(order_id: str, payload: dict) -> dict:
    """Charge a drafted order and return its new status."""
    order = _orders.find(order_id)
    if order is None:
        raise HTTPException(status_code=404, detail="order not found")
    order = _sales.charge(order, PaymentMethod(payload["method"]))
    return {"order_id": order.id, "status": order.status.value}
