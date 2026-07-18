"""SWCB official effective rainfall (alpha=0.7 daily) — Python reimplementation.

ORACLE: this must match the Java authority, not re-derive from it.
  service/SwcbEffectiveRainfall.java  +  test/.../SwcbEffectiveRainfallTest.java

  Rt = R0 + sum(i=1..6) alpha^i * Ri      alpha=0.7, WINDOW_DAYS=6 -> 7 calendar days
  offset = days_between(day, today) on the Asia/Taipei calendar;
  days with offset < 0 (future) or offset > 6 are ignored; result rounded 2dp HALF_UP.

  Verified example (from the JUnit oracle):
    [100, 100, 100] on today / today-1 / today-2
    = 100*0.7^0 + 100*0.7^1 + 100*0.7^2 = 100 + 70 + 49 = 219.00
"""
from __future__ import annotations

from datetime import date
from decimal import ROUND_HALF_UP, Decimal

ALPHA = Decimal("0.7")
WINDOW_DAYS = 6  # offset 0..6 inclusive == 7 calendar days


def effective_rainfall(daily: dict[date, float | Decimal], today: date) -> Decimal:
    """daily: {calendar_date (Asia/Taipei) -> daily rain mm}. Returns Rt rounded 2dp."""
    total = Decimal("0")
    for day, rain in daily.items():
        offset = (today - day).days
        if offset < 0 or offset > WINDOW_DAYS:
            continue
        total += Decimal(str(rain)) * (ALPHA ** offset)
    return total.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def signal(rt: Decimal | None, r70: Decimal | None, yellow_fraction: float = 0.8) -> str | None:
    """Rainfall warning light — matches SwcbEffectiveRainfall.signal(). None if no baseline."""
    if rt is None or r70 is None or r70 <= 0:
        return None
    if rt >= r70:
        return "RED"
    if rt >= r70 * Decimal(str(yellow_fraction)):
        return "YELLOW"
    return "GREEN"


# Self-check against the oracle so a broken build fails loudly at import time.
def _self_check() -> None:
    d0 = date(2026, 7, 18)
    sample = {d0: 100, date(2026, 7, 17): 100, date(2026, 7, 16): 100}
    got = effective_rainfall(sample, d0)
    assert got == Decimal("219.00"), f"SWCB oracle mismatch: {got} != 219.00"
    assert signal(Decimal("300"), Decimal("250")) == "RED"
    assert signal(Decimal("200"), Decimal("250")) == "YELLOW"
    assert signal(Decimal("199"), Decimal("250")) == "GREEN"


_self_check()
