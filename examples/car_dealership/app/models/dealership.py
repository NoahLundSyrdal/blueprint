"""Dealership location and inventory linking to Vehicle definitions."""

from __future__ import annotations

from dataclasses import dataclass, field

from app.models.vehicle import Vehicle, VehicleCondition


@dataclass
class InventoryVehicle:
    """A physical unit on a dealership lot; wraps a Vehicle model definition."""

    stock_number: str
    vehicle: Vehicle
    lot_location: str
    asking_price: float
    is_reserved: bool = False

    def apply_markdown(self, amount: float) -> None:
        self.asking_price = max(0.0, self.asking_price - amount)


@dataclass
class Dealership:
    """A single dealership location with its current inventory."""

    id: str
    name: str
    city: str
    state: str
    inventory: list[InventoryVehicle] = field(default_factory=list)

    def available_count(self) -> int:
        return sum(1 for v in self.inventory if not v.is_reserved)

    def new_vehicles(self) -> list[InventoryVehicle]:
        return [v for v in self.inventory if v.vehicle.condition == VehicleCondition.NEW]
