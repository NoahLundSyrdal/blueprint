"""Adapters implement outbound ports (payments, notifications)."""

from app.adapters.email_adapter import EmailNotificationAdapter
from app.adapters.stripe_adapter import StripePaymentAdapter

__all__ = ["EmailNotificationAdapter", "StripePaymentAdapter"]
