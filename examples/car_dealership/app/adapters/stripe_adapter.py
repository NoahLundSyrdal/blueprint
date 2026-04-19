"""Stripe-flavored implementation of the PaymentGateway port."""

from __future__ import annotations

import uuid
from datetime import datetime

from app.models.payment import PaymentMethod, PaymentRecord, PaymentStatus


class StripePaymentAdapter:
    """Stubbed payment adapter that behaves like a real gateway for demo purposes."""

    def __init__(self, api_key: str = "sk_test_demo") -> None:
        self.api_key = api_key
        self._authorized: dict[str, PaymentRecord] = {}

    def authorize(self, order_id: str, amount: float, method: PaymentMethod) -> PaymentRecord:
        record = PaymentRecord(
            id=f"pay_{uuid.uuid4().hex[:12]}",
            order_id=order_id,
            method=method,
            amount=amount,
            status=PaymentStatus.AUTHORIZED,
            reference=f"stripe:{uuid.uuid4().hex[:8]}",
            recorded_at=datetime.utcnow(),
        )
        self._authorized[record.id] = record
        return record

    def capture(self, payment: PaymentRecord) -> PaymentRecord:
        if payment.status != PaymentStatus.AUTHORIZED:
            raise ValueError(f"payment {payment.id} is not authorized")
        payment.status = PaymentStatus.CAPTURED
        payment.recorded_at = datetime.utcnow()
        return payment

    def refund(self, payment: PaymentRecord) -> PaymentRecord:
        if payment.status != PaymentStatus.CAPTURED:
            raise ValueError(f"payment {payment.id} is not captured")
        payment.status = PaymentStatus.REFUNDED
        payment.recorded_at = datetime.utcnow()
        return payment
