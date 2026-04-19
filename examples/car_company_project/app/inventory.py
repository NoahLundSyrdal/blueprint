from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import TYPE_CHECKING

from app.vehicle import VehicleModel

if TYPE_CHECKING:
    from app.dealership import Dealership


@dataclass
class InventoryVehicle:
    vin: str
    model: VehicleModel
    dealership: Dealership
    model_year: int
    color: str
    status: str
    listed_on: date

    def mark_sold(self) -> None:
        self.status = "sold"
