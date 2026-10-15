from app.core.skills.base import Skill
from app.core.skills.indicators import IndicatorsSkill


def build_skills(lookback_days: int) -> list[Skill]:
    """라우터가 고를 수 있는 스킬 목록"""
    return [IndicatorsSkill(lookback_days)]
