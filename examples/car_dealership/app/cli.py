"""Operator CLI — drive the dealership app from the terminal.

Blueprint will classify commands here as CLI components. Requires ``click``
(declared as an optional dependency in ``pyproject.toml``).
"""

from __future__ import annotations

import click

from app.adapters.email_adapter import EmailNotificationAdapter
from app.adapters.stripe_adapter import StripePaymentAdapter
from app.models.customer import Customer, LoyaltyTier
from app.models.dealership import Dealership, InventoryVehicle
from app.models.payment import PaymentMethod
from app.models.vehicle import GasVehicle, VehicleCondition
from app.repositories.customer_repository import CustomerRepository
from app.repositories.order_repository import OrderRepository
from app.repositories.vehicle_repository import VehicleRepository
from app.services.inventory_service import InventoryService
from app.services.pricing_service import PricingService
from app.services.sales_service import SalesService


@click.group()
def cli() -> None:
    """Car dealership operator tools."""


@cli.command("list-inventory")
@click.option("--dealership-id", required=True, help="Dealership to list.")
def list_inventory(dealership_id: str) -> None:
    """Print available inventory for a dealership."""
    service = _build_inventory_service()
    for vehicle in service.available(dealership_id):
        click.echo(f"{vehicle.stock_number}  {vehicle.vehicle.describe()}  ${vehicle.asking_price:.2f}")


@cli.command("quote")
@click.option("--dealership-id", required=True)
@click.option("--stock", required=True)
@click.option("--customer-id", required=True)
def quote(dealership_id: str, stock: str, customer_id: str) -> None:
    """Show a personalized quote for a customer+vehicle pair."""
    customers, vehicles = _seed()
    pricing = PricingService()
    customer = customers.find(customer_id)
    inventory = vehicles.find_by_stock(dealership_id, stock)
    if customer is None or inventory is None:
        raise click.ClickException("customer or vehicle not found")
    click.echo(f"quote: ${pricing.quote(inventory, customer):.2f}")


@cli.command("sell")
@click.option("--dealership-id", required=True)
@click.option("--stock", required=True)
@click.option("--customer-id", required=True)
@click.option("--method", type=click.Choice([m.value for m in PaymentMethod]), default="credit_card")
def sell(dealership_id: str, stock: str, customer_id: str, method: str) -> None:
    """Create, charge, and deliver an order end-to-end."""
    customers, vehicles = _seed()
    orders = OrderRepository()
    sales = SalesService(
        orders=orders,
        inventory=InventoryService(vehicles),
        pricing=PricingService(),
        payment_gateway=StripePaymentAdapter(),
        notifications=EmailNotificationAdapter(),
    )
    customer = customers.find(customer_id)
    if customer is None:
        raise click.ClickException(f"customer {customer_id} not found")
    order = sales.draft_order(customer, dealership_id, [stock])
    order = sales.charge(order, PaymentMethod(method))
    order = sales.deliver(order)
    click.echo(f"order {order.id} delivered, status={order.status.value}")


def _seed() -> tuple[CustomerRepository, VehicleRepository]:
    customers = CustomerRepository()
    customers.save(Customer(id="cust1", name="Ada", email="ada@example.com", phone="555", loyalty_tier=LoyaltyTier.GOLD))

    vehicles = VehicleRepository()
    dealership = Dealership(id="d1", name="Blueprint Motors", city="Oslo", state="NO")
    dealership.inventory.append(
        InventoryVehicle(
            stock_number="A100",
            vehicle=GasVehicle(
                vin="VIN1",
                make="Honda",
                model="Civic",
                year=2024,
                base_price=25000.0,
                condition=VehicleCondition.NEW,
                mpg_city=32,
                mpg_highway=42,
                fuel_tank_gallons=12.4,
            ),
            lot_location="A1",
            asking_price=25000.0,
        )
    )
    vehicles.save_dealership(dealership)
    return customers, vehicles


def _build_inventory_service() -> InventoryService:
    _, vehicles = _seed()
    return InventoryService(vehicles)


if __name__ == "__main__":
    cli()
