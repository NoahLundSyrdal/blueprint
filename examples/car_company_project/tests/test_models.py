from datetime import date
from pathlib import Path
import sys

sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.models import CarCompany, Dealership, VehicleModel


def test_stock_vehicle_creates_available_inventory_vehicle():
    company = CarCompany(id="company-1", name="Northwind Motors", headquarters_city="Oslo")
    model = VehicleModel(id="model-1", name="Falcon", segment="SUV", company=company)
    dealership = Dealership(id="dealer-1", name="Northwind Downtown", city="Trondheim", company=company)

    vehicle = dealership.stock_vehicle("VIN-001", model, 2026, "midnight blue")

    assert vehicle.dealership == dealership
    assert vehicle.model == model
    assert vehicle.status == "available"
    assert vehicle.listed_on == date.today()


def test_inventory_vehicle_can_be_marked_sold():
    company = CarCompany(id="company-1", name="Northwind Motors", headquarters_city="Oslo")
    model = VehicleModel(id="model-1", name="Falcon", segment="SUV", company=company)
    dealership = Dealership(id="dealer-1", name="Northwind Downtown", city="Trondheim", company=company)
    vehicle = dealership.stock_vehicle("VIN-002", model, 2026, "silver")

    vehicle.mark_sold()

    assert vehicle.status == "sold"
