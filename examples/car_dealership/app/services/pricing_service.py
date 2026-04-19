"""Quote prices, applying loyalty discounts and condition multipliers."""

from __future__ import annotations

from app.models.customer import Customer, LoyaltyTier
from app.models.dealership import InventoryVehicle
from app.models.vehicle import VehicleCondition


class PricingService:
    """Pure pricing logic, no I/O."""

    LOYALTY_DISCOUNTS: dict[LoyaltyTier, float] = {
        LoyaltyTier.STANDARD: 0.00,
        LoyaltyTier.SILVER: 0.02,
        LoyaltyTier.GOLD: 0.05,
        LoyaltyTier.PLATINUM: 0.08,
    }

    CONDITION_MULTIPLIERS: dict[VehicleCondition, float] = {
        VehicleCondition.NEW: 1.00,
        VehicleCondition.CERTIFIED_PRE_OWNED: 0.88,
        VehicleCondition.USED: 0.75,
    }

    def list_price(self, inventory_vehicle: InventoryVehicle) -> float:
        base = inventory_vehicle.vehicle.base_price
        multiplier = self.CONDITION_MULTIPLIERS[inventory_vehicle.vehicle.condition]
        return round(base * multiplier, 2)

    def quote(self, inventory_vehicle: InventoryVehicle, customer: Customer) -> float:
        listed = self.list_price(inventory_vehicle)
        discount = self.LOYALTY_DISCOUNTS[customer.loyalty_tier]
        return round(listed * (1.0 - discount), 2)
