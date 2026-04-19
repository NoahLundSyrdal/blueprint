"""Sales orchestration — the center of the call graph."""

from __future__ import annotations

import uuid
from datetime import datetime

from app.models.customer import Customer
from app.models.payment import PaymentMethod
from app.models.sales import LineItem, OrderStatus, SalesOrder
from app.ports.notification_port import NotificationPort
from app.ports.payment_port import PaymentGateway
from app.repositories.order_repository import OrderRepository
from app.services.inventory_service import InventoryService
from app.services.pricing_service import PricingService


class SalesService:
    """Wires inventory, pricing, payment, and notifications into one order flow."""

    def __init__(
        self,
        orders: OrderRepository,
        inventory: InventoryService,
        pricing: PricingService,
        payment_gateway: PaymentGateway,
        notifications: NotificationPort,
    ) -> None:
        self.orders = orders
        self.inventory = inventory
        self.pricing = pricing
        self.payment_gateway = payment_gateway
        self.notifications = notifications

    def draft_order(
        self,
        customer: Customer,
        dealership_id: str,
        stock_numbers: list[str],
        warranty_months: int = 0,
    ) -> SalesOrder:
        items: list[LineItem] = []
        for stock in stock_numbers:
            reserved = self.inventory.reserve(dealership_id, stock)
            sale_price = self.pricing.quote(reserved, customer)
            items.append(
                LineItem(
                    inventory_vehicle=reserved,
                    sale_price=sale_price,
                    warranty_months=warranty_months,
                )
            )
        order = SalesOrder(
            id=f"ord_{uuid.uuid4().hex[:10]}",
            customer=customer,
            dealership_id=dealership_id,
            items=items,
            status=OrderStatus.QUOTED,
            created_at=datetime.utcnow(),
        )
        return self.orders.save(order)

    def charge(self, order: SalesOrder, method: PaymentMethod) -> SalesOrder:
        if order.status not in (OrderStatus.QUOTED, OrderStatus.AWAITING_PAYMENT):
            raise ValueError(f"order {order.id} cannot be charged in status {order.status}")
        order.status = OrderStatus.AWAITING_PAYMENT
        payment = self.payment_gateway.authorize(order.id, order.subtotal(), method)
        payment = self.payment_gateway.capture(payment)
        order.payments.append(payment)
        if order.balance_due() == 0.0:
            order.status = OrderStatus.PAID
            self.notifications.send_order_confirmation(order.customer, order)
        return self.orders.save(order)

    def deliver(self, order: SalesOrder) -> SalesOrder:
        if order.status != OrderStatus.PAID:
            raise ValueError(f"order {order.id} not paid")
        order.status = OrderStatus.DELIVERED
        self.notifications.send_delivery_notice(order.customer, order)
        return self.orders.save(order)

    def cancel(self, order: SalesOrder) -> SalesOrder:
        for item in order.items:
            self.inventory.release(order.dealership_id, item.inventory_vehicle.stock_number)
        order.status = OrderStatus.CANCELLED
        return self.orders.save(order)
