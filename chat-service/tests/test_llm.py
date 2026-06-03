from app.llm import build_analysis_messages, DISCLAIMER, SYSTEM_GUARD


def test_analysis_messages_include_system_guard():
    msgs = build_analysis_messages(
        symbol="AAPL", summary="RSI 60(중립), MA20>MA60", question="어때?"
    )
    assert msgs[0]["role"] == "system"
    assert SYSTEM_GUARD in msgs[0]["content"]
    # 지표 요약과 종목이 유저 메시지에 포함
    user = msgs[1]["content"]
    assert "AAPL" in user
    assert "RSI 60(중립)" in user


def test_disclaimer_text_present():
    assert "투자 조언이 아니" in DISCLAIMER
