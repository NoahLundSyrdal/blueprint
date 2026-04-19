"""Vehicle inventory persistence — in-memory implementation."""

from __future__ import annotations

from typing import Optional

from app.models.dealership import Dealership, InventoryVehicle


class VehicleRepository:
    """CRUD for Dealership + InventoryVehicle aggregates."""

    def __init__(self) -> None:
        self._dealerships: dict[str, Dealership] = {}

    def save_dealership(self, dealership: Dealership) -> Dealership:
        self._dealerships[dealership.id] = dealership
        return dealership

    def find_dealership(self, dealership_id: str) -> Optional[Dealership]:
        return self._dealerships.get(dealership_id)

    def find_available(self, dealership_id: str) -> list[InventoryVehicle]:
        dealership = self._dealerships.get(dealership_id)
        if dealership is None:
            return []
        return [v for v in dealership.inventory if not v.is_reserved]

    def find_by_stock(self, dealership_id: str, stock_number: str) -> Optional[InventoryVehicle]:
        dealership = self._dealerships.get(dealership_id)
        if dealership is None:
            return None
        for vehicle in dealership.inventory:
            if vehicle.stock_number == stock_number:
                return vehicle
        return None
