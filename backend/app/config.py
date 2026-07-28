from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    database_url: str


def load_settings() -> Settings:
    return Settings(
        database_url=os.getenv(
            "GROUP_AAC_DATABASE_URL",
            "sqlite:///./group_aac_backend.db",
        )
    )
