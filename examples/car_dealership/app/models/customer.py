"""Customer records and loyalty tiers."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class LoyaltyTier(Enum):
    """Loyalty program tiers used by PricingService for discount tiers."""

    STANDARD = "standard"
    SILVER = "silver"
    GOLD = "gold"
    PLATINUM = "platinum"


@dataclass
class Customer:
    """A buyer on file with the dealership."""

    id: str
    name: str
    email: str
    phone: str
    loyalty_tier: LoyaltyTier = LoyaltyTier.STANDARD

    def is_preferred(self) -> bool:
        return self.loyalty_tier in (LoyaltyTier.GOLD, LoyaltyTier.PLATINUM)
