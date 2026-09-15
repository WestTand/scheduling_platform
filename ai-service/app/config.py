import os
from dataclasses import dataclass


def _positive_float(value: str | None, default: float) -> float:
    try:
        parsed = float(value) if value is not None else default
    except ValueError:
        return default
    return parsed if parsed > 0 else default


@dataclass(frozen=True)
class GeminiSettings:
    api_key: str | None
    model: str
    timeout_seconds: float

    @classmethod
    def from_environment(cls) -> "GeminiSettings":
        api_key = os.getenv("GEMINI_API_KEY")
        return cls(
            api_key=api_key.strip() if api_key else None,
            model=os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite"),
            timeout_seconds=_positive_float(os.getenv("GEMINI_TIMEOUT_SECONDS"), 15),
        )
