from warehouse.domain.models import Shipment


class WarehouseService:
    def create_shipment(self, tracking_code: str, carrier: str) -> Shipment:
        return Shipment(tracking_code=tracking_code, carrier=carrier)
