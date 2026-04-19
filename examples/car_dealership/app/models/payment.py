"""Payment value objects: methods, statuses, records."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum


class PaymentMethod(Enum):
    """Accepted payment rails."""

    CASH = "cash"
    CREDIT_CARD = "credit_card"
    FINANCING = "financing"
    TRADE_IN = "trade_in"


class PaymentStatus(Enum):
    PENDING = "pending"
    AUTHORIZED = "authorized"
    CAPTURED = "captured"
    DECLINED = "declined"
    REFUNDED = "refunded"


@dataclass
class PaymentRecord:
    """A single payment attempt against an order."""

    id: str
    order_id: str
    method: PaymentMethod
    amount: float
    status: PaymentStatus = PaymentStatus.PENDING
    reference: str = ""
    recorded_at: datetime | None = None
