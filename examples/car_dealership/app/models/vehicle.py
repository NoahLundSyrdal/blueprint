"""Vehicle taxonomy: base Vehicle plus drivetrain-specific subclasses."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class VehicleCondition(Enum):
    """Inspection condition set by the dealership when a vehicle is onboarded."""

    NEW = "new"
    CERTIFIED_PRE_OWNED = "certified_pre_owned"
    USED = "used"


@dataclass
class Vehicle:
    """A make/model/year record; the canonical product definition."""

    vin: str
    make: str
    model: str
    year: int
    base_price: float
    condition: VehicleCondition = VehicleCondition.NEW
    features: list[str] = field(default_factory=list)

    def describe(self) -> str:
        return f"{self.year} {self.make} {self.model} ({self.condition.value})"


@dataclass
class GasVehicle(Vehicle):
    """Internal combustion vehicle — extends the base Vehicle model."""

    mpg_city: int = 0
    mpg_highway: int = 0
    fuel_tank_gallons: float = 0.0


@dataclass
class ElectricVehicle(Vehicle):
    """Battery-electric vehicle — extends the base Vehicle model."""

    battery_kwh: float = 0.0
    range_miles: int = 0
    fast_charge_kw: float = 0.0
