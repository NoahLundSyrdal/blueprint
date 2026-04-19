"""InventoryService and PricingService behavior."""

from __future__ import annotations

import pytest

from app.models.customer import LoyaltyTier
from app.repositories.customer_repository import CustomerRepository
from app.repositories.vehicle_repository import VehicleRepository
from app.services.inventory_service import InventoryService
from app.services.pricing_service import PricingService


def test_available_lists_unreserved(vehicles: VehicleRepository) -> None:
    service = InventoryService(vehicles)
    stocks = {v.stock_number for v in service.available("d_oslo")}
    assert stocks == {"A100", "B200", "C300"}


def test_reserve_then_release(vehicles: VehicleRepository) -> None:
    service = InventoryService(vehicles)
    service.reserve("d_oslo", "A100")
    assert {v.stock_number for v in service.available("d_oslo")} == {"B200", "C300"}
    service.release("d_oslo", "A100")
    assert {v.stock_number for v in service.available("d_oslo")} == {"A100", "B200", "C300"}


def test_reserve_double_raises(vehicles: VehicleRepository) -> None:
    service = InventoryService(vehicles)
    service.reserve("d_oslo", "A100")
    with pytest.raises(ValueError):
        service.reserve("d_oslo", "A100")


def test_reserve_missing_raises(vehicles: VehicleRepository) -> None:
    service = InventoryService(vehicles)
    with pytest.raises(LookupError):
        service.reserve("d_oslo", "ZZZZ")


def test_pricing_quote_applies_loyalty_and_condition(
    customers: CustomerRepository,
    vehicles: VehicleRepository,
) -> None:
    pricing = PricingService()
    ada = customers.find("cust_ada")
    assert ada is not None and ada.loyalty_tier == LoyaltyTier.GOLD
    new_car = vehicles.find_by_stock("d_oslo", "A100")
    cpo_car = vehicles.find_by_stock("d_oslo", "C300")
    assert new_car is not None and cpo_car is not None

    # NEW (1.00) * GOLD (1 - 0.05) = 0.95 of base
    assert pricing.quote(new_car, ada) == round(25000.0 * 0.95, 2)
    # CPO (0.88) * GOLD (0.95) = 0.836 of base
    assert pricing.quote(cpo_car, ada) == round(18000.0 * 0.88 * 0.95, 2)
