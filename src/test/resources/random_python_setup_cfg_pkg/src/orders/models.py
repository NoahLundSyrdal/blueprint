from dataclasses import dataclass


@dataclass
class OrderRecord:
    order_id: str
    status: str
    total_cents: int
