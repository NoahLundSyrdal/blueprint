"""In-memory repositories. Replace with DB-backed adapters in production."""

from app.repositories.customer_repository import CustomerRepository
from app.repositories.order_repository import OrderRepository
from app.repositories.vehicle_repository import VehicleRepository

__all__ = ["CustomerRepository", "OrderRepository", "VehicleRepository"]
