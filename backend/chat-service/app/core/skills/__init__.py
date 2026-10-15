from app.core.skills.base import Skill
from app.core.skills.dividend import DividendSkill
from app.core.skills.indicators import IndicatorsSkill
from app.core.skills.trend import TrendSkill


def build_skills(lookback_days: int, dividend_years: int) -> list[Skill]:
    """라우터가 고를 수 있는 스킬 목록"""
    return [IndicatorsSkill(lookback_days), TrendSkill(lookback_days), DividendSkill(dividend_years)]
