"""Payment gateway port — implemented by adapters (Stripe, cash, etc.)."""

from __future__ import annotations

from typing import Protocol

from app.models.payment import PaymentMethod, PaymentRecord


class PaymentGateway(Protocol):
    """Structural interface for any payment gateway adapter."""

    def authorize(self, order_id: str, amount: float, method: PaymentMethod) -> PaymentRecord:
        ...

    def capture(self, payment: PaymentRecord) -> PaymentRecord:
        ...

    def refund(self, payment: PaymentRecord) -> PaymentRecord:
        ...
