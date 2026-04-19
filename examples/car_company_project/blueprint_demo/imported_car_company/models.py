from dataclasses import dataclass

@dataclass
class CarCompany:
    id: str
    name: str
    headquarters_city: str

@dataclass
class Dealership:
    id: str
    name: str
    city: str
    company: CarCompany

@dataclass
class VehicleModel:
    id: str
    name: str
    segment: str
    company: CarCompany

@dataclass
class InventoryVehicle:
    vin: str
    model: VehicleModel
    dealership: Dealership
    model_year: int
    color: str
    status: str
    listed_on: date
