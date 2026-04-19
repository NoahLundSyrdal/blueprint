"""Customer persistence — in-memory implementation."""

from __future__ import annotations

from typing import Optional

from app.models.customer import Customer


class CustomerRepository:
    """CRUD for Customer aggregates."""

    def __init__(self) -> None:
        self._customers: dict[str, Customer] = {}

    def save(self, customer: Customer) -> Customer:
        self._customers[customer.id] = customer
        return customer

    def find(self, customer_id: str) -> Optional[Customer]:
        return self._customers.get(customer_id)

    def find_by_email(self, email: str) -> Optional[Customer]:
        for customer in self._customers.values():
            if customer.email == email:
                return customer
        return None

    def all(self) -> list[Customer]:
        return list(self._customers.values())
