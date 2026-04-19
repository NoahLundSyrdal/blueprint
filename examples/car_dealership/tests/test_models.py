"""Sanity checks on the domain model dataclasses and enums."""

from __future__ import annotations

from app.models.customer import Customer, LoyaltyTier
from app.models.dealership import Dealership, InventoryVehicle
from app.models.sales import LineItem, OrderStatus, SalesOrder
from app.models.vehicle import ElectricVehicle, GasVehicle, VehicleCondition


def test_gas_vehicle_extends_base() -> None:
    car = GasVehicle(
        vin="X1",
        make="Ford",
        model="Focus",
        year=2023,
        base_price=21000.0,
        mpg_city=30,
        mpg_highway=40,
    )
    assert car.describe() == "2023 Ford Focus (new)"
    assert car.condition == VehicleCondition.NEW


def test_electric_vehicle_extends_base() -> None:
    ev = ElectricVehicle(
        vin="X2",
        make="Tesla",
        model="Model Y",
        year=2024,
        base_price=52000.0,
        battery_kwh=82.0,
        range_miles=330,
    )
    assert ev.range_miles == 330
    assert ev.condition == VehicleCondition.NEW


def test_customer_preferred_detection() -> None:
    gold = Customer(id="c1", name="A", email="a@x", phone="", loyalty_tier=LoyaltyTier.GOLD)
    standard = Customer(id="c2", name="B", email="b@x", phone="")
    assert gold.is_preferred()
    assert not standard.is_preferred()


def test_dealership_counts_available_only() -> None:
    inv = InventoryVehicle(
        stock_number="S1",
        vehicle=GasVehicle(vin="v", make="m", model="x", year=2020, base_price=1.0),
        lot_location="L",
        asking_price=1.0,
    )
    reserved = InventoryVehicle(
        stock_number="S2",
        vehicle=GasVehicle(vin="v2", make="m", model="x", year=2020, base_price=1.0),
        lot_location="L",
        asking_price=1.0,
        is_reserved=True,
    )
    d = Dealership(id="d", name="n", city="c", state="s", inventory=[inv, reserved])
    assert d.available_count() == 1
    assert d.new_vehicles() == [inv, reserved]


def test_sales_order_subtotal_and_balance() -> None:
    customer = Customer(id="c", name="N", email="e", phone="")
    item = LineItem(
        inventory_vehicle=InventoryVehicle(
            stock_number="S",
            vehicle=GasVehicle(vin="v", make="m", model="x", year=2020, base_price=1.0),
            lot_location="L",
            asking_price=100.0,
        ),
        sale_price=100.0,
    )
    order = SalesOrder(id="o", customer=customer, dealership_id="d", items=[item], status=OrderStatus.DRAFT)
    assert order.subtotal() == 100.0
    assert order.balance_due() == 100.0
