"""Background jobs. Blueprint classifies these as JOB components."""

from __future__ import annotations

from datetime import datetime, timedelta

from app.adapters.email_adapter import EmailNotificationAdapter
from app.models.sales import OrderStatus
from app.repositories.order_repository import OrderRepository


class StaleQuoteReminderJob:
    """Emails customers whose quoted orders haven't been charged in 48h."""

    def __init__(self, orders: OrderRepository, notifications: EmailNotificationAdapter) -> None:
        self.orders = orders
        self.notifications = notifications

    def execute(self, now: datetime | None = None) -> int:
        current = now or datetime.utcnow()
        stale_cutoff = current - timedelta(hours=48)
        stale = [
            o
            for o in self.orders.by_status(OrderStatus.QUOTED)
            if o.created_at is not None and o.created_at < stale_cutoff
        ]
        for order in stale:
            self.notifications.send_order_confirmation(order.customer, order)
        return len(stale)


def cleanup_cancelled_orders_task(orders: OrderRepository) -> int:
    """Purge cancelled orders older than 30 days (module-level task function)."""
    cutoff = datetime.utcnow() - timedelta(days=30)
    to_purge = [
        o
        for o in orders.by_status(OrderStatus.CANCELLED)
        if o.created_at is not None and o.created_at < cutoff
    ]
    for order in to_purge:
        orders._orders.pop(order.id, None)
    return len(to_purge)
