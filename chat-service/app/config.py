from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    openai_api_key: str = ""
    openai_model_main: str = "gpt-4o"
    openai_model_router: str = "gpt-4o-mini"

    redis_host: str = "redis"
    redis_port: int = 6379

    market_data_base_url: str = "http://market-data-service:8083"

    keycloak_jwks_url: str = (
        "http://keycloak:8080/realms/muscathan/protocol/openid-connect/certs"
    )

    daily_limit: int = 5
    ohlc_lookback_days: int = 300
    max_answer_tokens: int = 700


settings = Settings()
