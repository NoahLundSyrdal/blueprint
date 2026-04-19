from dataclasses import dataclass
from datetime import date

from app.company import CarCompany
from app.inventory import InventoryVehicle
from app.vehicle import VehicleModel


@dataclass
class Dealership:
    id: str
    name: str
    city: str
    company: CarCompany

    def stock_vehicle(self, vin: str, model: VehicleModel, model_year: int, color: str) -> InventoryVehicle:
        return InventoryVehicle(
            vin=vin,
            model=model,
            dealership=self,
            model_year=model_year,
            color=color,
            status="available",
            listed_on=date.today(),
        )
