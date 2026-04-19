"""Inventory lookups and reservations."""

from __future__ import annotations

from app.models.dealership import Dealership, InventoryVehicle
from app.repositories.vehicle_repository import VehicleRepository


class InventoryService:
    """Reads/writes vehicle inventory through the VehicleRepository."""

    def __init__(self, vehicles: VehicleRepository) -> None:
        self.vehicles = vehicles

    def register_dealership(self, dealership: Dealership) -> Dealership:
        return self.vehicles.save_dealership(dealership)

    def available(self, dealership_id: str) -> list[InventoryVehicle]:
        return self.vehicles.find_available(dealership_id)

    def reserve(self, dealership_id: str, stock_number: str) -> InventoryVehicle:
        vehicle = self.vehicles.find_by_stock(dealership_id, stock_number)
        if vehicle is None:
            raise LookupError(f"stock {stock_number} not found at {dealership_id}")
        if vehicle.is_reserved:
            raise ValueError(f"stock {stock_number} already reserved")
        vehicle.is_reserved = True
        return vehicle

    def release(self, dealership_id: str, stock_number: str) -> InventoryVehicle:
        vehicle = self.vehicles.find_by_stock(dealership_id, stock_number)
        if vehicle is None:
            raise LookupError(f"stock {stock_number} not found at {dealership_id}")
        vehicle.is_reserved = False
        return vehicle
