"""Email notification adapter — concrete subclass of NotificationPort."""

from __future__ import annotations

from app.models.customer import Customer
from app.models.sales import SalesOrder
from app.ports.notification_port import NotificationPort


class EmailNotificationAdapter(NotificationPort):
    """Collects outbound messages in memory; a real impl would SMTP them."""

    def __init__(self, sender: str = "sales@car-dealership.example") -> None:
        self.sender = sender
        self.sent: list[tuple[str, str, str]] = []

    def send_order_confirmation(self, customer: Customer, order: SalesOrder) -> None:
        subject = f"Order {order.id} confirmed"
        body = (
            f"Hi {customer.name}, your order is confirmed "
            f"with {len(order.items)} vehicle(s). Subtotal: ${order.subtotal():.2f}."
        )
        self.sent.append((customer.email, subject, body))

    def send_delivery_notice(self, customer: Customer, order: SalesOrder) -> None:
        subject = f"Order {order.id} ready for delivery"
        body = f"Hi {customer.name}, your vehicle is ready. Please schedule pickup."
        self.sent.append((customer.email, subject, body))
