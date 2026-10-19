from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str = ""
    openai_model_main: str = "gpt-4o"
    openai_model_router: str = "gpt-4o-mini"

    redis_host: str = "redis"
    redis_port: int = 6379
    redis_password: str = ""  # 비어 있으면 보내지 않는다

    market_data_base_url: str = "http://market-data-service:8083"

    keycloak_jwks_url: str = (
        "http://keycloak:8080/realms/muscathan/protocol/openid-connect/certs"
    )
    keycloak_issuers: str = "http://keycloak:8080/realms/muscathan"  # 받는 토큰의 iss, 쉼표로 여럿
    keycloak_client_ids: str = "ggeolmuse-client,ggeolmuse-frontend"  # 받는 토큰의 azp, 쉼표로 여럿

    daily_limit: int = 5
    daily_reject_limit: int = 20
    ohlc_lookback_days: int = 400
    dividend_lookback_years: int = 3
    max_answer_tokens: int = 700

    conversation_ttl_hours: int = 5
    history_turns: int = 3
    history_answer_chars: int = 800

    market_timeout_seconds: float = 5.0
    router_timeout_seconds: float = 5.0
    answer_timeout_seconds: float = 15.0


settings = Settings()
