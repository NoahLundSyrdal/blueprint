"""Notification port — ABC that adapters inherit from."""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.models.customer import Customer
from app.models.sales import SalesOrder


class NotificationPort(ABC):
    """Nominal interface for notifying a customer about their order."""

    @abstractmethod
    def send_order_confirmation(self, customer: Customer, order: SalesOrder) -> None:
        ...

    @abstractmethod
    def send_delivery_notice(self, customer: Customer, order: SalesOrder) -> None:
        ...
