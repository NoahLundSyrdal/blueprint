from dataclasses import dataclass
from datetime import date


@dataclass
class CarCompany:
    id: str
    name: str
    headquarters_city: str


@dataclass
class VehicleModel:
    id: str
    name: str
    segment: str
    company: CarCompany

    def display_name(self) -> str:
        return f"{self.company.name} {self.name}"


@dataclass
class Dealership:
    id: str
    name: str
    city: str
    company: CarCompany

    def stock_vehicle(self, vin: str, model: VehicleModel, model_year: int, color: str) -> "InventoryVehicle":
        return InventoryVehicle(
            vin=vin,
            model=model,
            dealership=self,
            model_year=model_year,
            color=color,
            status="available",
            listed_on=date.today(),
        )


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

