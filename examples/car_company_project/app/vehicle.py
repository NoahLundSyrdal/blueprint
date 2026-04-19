from dataclasses import dataclass

from app.company import CarCompany


@dataclass
class VehicleModel:
    id: str
    name: str
    segment: str
    company: CarCompany

    def display_name(self) -> str:
        return f"{self.company.name} {self.name}"
