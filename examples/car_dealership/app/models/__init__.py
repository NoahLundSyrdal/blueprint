"""Domain models for the car dealership."""

from app.models.customer import Customer, LoyaltyTier
from app.models.dealership import Dealership, InventoryVehicle
from app.models.payment import PaymentMethod, PaymentRecord, PaymentStatus
from app.models.sales import LineItem, OrderStatus, SalesOrder
from app.models.vehicle import ElectricVehicle, GasVehicle, Vehicle, VehicleCondition

__all__ = [
    "Customer",
    "Dealership",
    "ElectricVehicle",
    "GasVehicle",
    "InventoryVehicle",
    "LineItem",
    "LoyaltyTier",
    "OrderStatus",
    "PaymentMethod",
    "PaymentRecord",
    "PaymentStatus",
    "SalesOrder",
    "Vehicle",
    "VehicleCondition",
]
