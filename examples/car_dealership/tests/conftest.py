"""Shared fixtures — a seeded dealership, customer, and full service wiring."""

from __future__ import annotations

import pytest

from app.adapters.email_adapter import EmailNotificationAdapter
from app.adapters.stripe_adapter import StripePaymentAdapter
from app.models.customer import Customer, LoyaltyTier
from app.models.dealership import Dealership, InventoryVehicle
from app.models.vehicle import ElectricVehicle, GasVehicle, VehicleCondition
from app.repositories.customer_repository import CustomerRepository
from app.repositories.order_repository import OrderRepository
from app.repositories.vehicle_repository import VehicleRepository
from app.services.inventory_service import InventoryService
from app.services.pricing_service import PricingService
from app.services.sales_service import SalesService


@pytest.fixture
def customers() -> CustomerRepository:
    repo = CustomerRepository()
    repo.save(
        Customer(
            id="cust_ada",
            name="Ada Lovelace",
            email="ada@example.com",
            phone="555-0101",
            loyalty_tier=LoyaltyTier.GOLD,
        )
    )
    repo.save(
        Customer(
            id="cust_basic",
            name="Casual Carl",
            email="carl@example.com",
            phone="555-0102",
            loyalty_tier=LoyaltyTier.STANDARD,
        )
    )
    return repo


@pytest.fixture
def vehicles() -> VehicleRepository:
    repo = VehicleRepository()
    dealership = Dealership(id="d_oslo", name="Blueprint Motors Oslo", city="Oslo", state="NO")
    dealership.inventory.extend(
        [
            InventoryVehicle(
                stock_number="A100",
                vehicle=GasVehicle(
                    vin="VIN-A100",
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
            ),
            InventoryVehicle(
                stock_number="B200",
                vehicle=ElectricVehicle(
                    vin="VIN-B200",
                    make="Tesla",
                    model="Model 3",
                    year=2024,
                    base_price=42000.0,
                    condition=VehicleCondition.NEW,
                    battery_kwh=75.0,
                    range_miles=272,
                    fast_charge_kw=170.0,
                ),
                lot_location="B2",
                asking_price=42000.0,
            ),
            InventoryVehicle(
                stock_number="C300",
                vehicle=GasVehicle(
                    vin="VIN-C300",
                    make="Toyota",
                    model="Camry",
                    year=2022,
                    base_price=18000.0,
                    condition=VehicleCondition.CERTIFIED_PRE_OWNED,
                    mpg_city=28,
                    mpg_highway=39,
                    fuel_tank_gallons=14.5,
                ),
                lot_location="C3",
                asking_price=16000.0,
            ),
        ]
    )
    repo.save_dealership(dealership)
    return repo


@pytest.fixture
def orders() -> OrderRepository:
    return OrderRepository()


@pytest.fixture
def sales_service(
    orders: OrderRepository,
    vehicles: VehicleRepository,
) -> SalesService:
    return SalesService(
        orders=orders,
        inventory=InventoryService(vehicles),
        pricing=PricingService(),
        payment_gateway=StripePaymentAdapter(),
        notifications=EmailNotificationAdapter(),
    )
