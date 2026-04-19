"""Background job behavior (StaleQuoteReminderJob)."""

from __future__ import annotations

from datetime import datetime, timedelta

from app.adapters.email_adapter import EmailNotificationAdapter
from app.jobs import StaleQuoteReminderJob
from app.models.customer import Customer
from app.models.sales import OrderStatus, SalesOrder
from app.repositories.order_repository import OrderRepository


def _order_aged(hours: int, order_id: str) -> SalesOrder:
    customer = Customer(id="c", name="N", email="e@e", phone="")
    return SalesOrder(
        id=order_id,
        customer=customer,
        dealership_id="d",
        status=OrderStatus.QUOTED,
        created_at=datetime.utcnow() - timedelta(hours=hours),
    )


def test_stale_reminder_emails_old_quotes() -> None:
    repo = OrderRepository()
    repo.save(_order_aged(hours=72, order_id="old"))
    repo.save(_order_aged(hours=1, order_id="fresh"))
    notifier = EmailNotificationAdapter()
    job = StaleQuoteReminderJob(repo, notifier)

    sent_count = job.execute()

    assert sent_count == 1
    assert len(notifier.sent) == 1
