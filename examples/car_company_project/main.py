from app.models import CarCompany, Dealership, VehicleModel


def main() -> None:
    company = CarCompany(id="company-1", name="Northwind Motors", headquarters_city="Oslo")
    model = VehicleModel(id="model-1", name="Falcon", segment="SUV", company=company)
    dealership = Dealership(id="dealer-1", name="Northwind Downtown", city="Trondheim", company=company)

    vehicle = dealership.stock_vehicle("VIN-001", model, 2026, "midnight blue")
    print(f"{dealership.name} stocked {vehicle.model.display_name()} as {vehicle.status}")


if __name__ == "__main__":
    main()
