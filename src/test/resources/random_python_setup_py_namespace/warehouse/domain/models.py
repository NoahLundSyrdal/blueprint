from dataclasses import dataclass


@dataclass
class Shipment:
    tracking_code: str
    carrier: str
