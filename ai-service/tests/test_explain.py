import json
from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.main import app
from app.config import GeminiSettings
from app.services import llm

client = TestClient(app)


def explain_request() -> dict:
    return {
        "schemaVersion": "1.0",
        "runId": 10,
        "period": {
            "periodId": 20,
            "startDate": "2026-09-14",
            "endDate": "2026-09-15",
            "versionNo": 1,
            "timezone": "Asia/Ho_Chi_Minh",
        },
        "status": "INFEASIBLE",
        "solverStatus": "INFEASIBLE",
        "diagnostics": [
            {
                "code": "INSUFFICIENT_AVAILABLE_STAFF",
                "message": "Approved leave leaves too few employees for this shift",
                "employeeId": None,
                "workShiftId": 1000,
            }
        ],
        "statistics": {"wallTimeSeconds": 0.04, "conflicts": 0, "branches": 0},
        "rules": {
            "hardRules": {
                "minRestHoursBetweenShifts": 12,
                "maxWorkHoursPerDay": 12,
                "maxShiftsPerWeek": 6,
            },
            "quotas": {
                "overtimeShiftsPerWeek": 1,
                "bathingShiftsPerWeek": 2,
                "onCallEveryNDays": 6,
            },
            "softWeights": {
                "fairness": 10,
                "preference": 5,
                "weekend": 5,
                "heavyShift": 8,
                "workHourBalance": 10,
            },
        },
    }


def test_explain_uses_gemini_response(monkeypatch) -> None:
    def fake_call(settings, prompt: str) -> str:
        evidence = json.loads(prompt)
        assert evidence["status"] == "INFEASIBLE"
        assert "assignments" not in evidence
        assert evidence["diagnostics"][0]["code"] == "INSUFFICIENT_AVAILABLE_STAFF"
        return json.dumps(
            {
                "explanation": "Không đủ nhân viên khả dụng cho ca cần bố trí.",
                "suggestedActions": ["Rà soát nghỉ phép đã duyệt."],
            }
        )

    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.setattr(llm, "_call_gemini", fake_call)

    response = client.post("/api/v1/scheduling/explain", json=explain_request())

    assert response.status_code == 200
    assert response.json() == {
        "schemaVersion": "1.0",
        "runId": 10,
        "explanation": "Không đủ nhân viên khả dụng cho ca cần bố trí.",
        "suggestedActions": ["Rà soát nghỉ phép đã duyệt."],
        "usedModel": "gemini-3.5-flash-lite",
        "fallback": False,
    }


def test_gemini_call_requests_structured_json(monkeypatch) -> None:
    captured = {}

    class FakeClient:
        def __init__(self, **kwargs) -> None:
            captured["client"] = kwargs
            self.models = self

        def generate_content(self, **kwargs):
            captured["request"] = kwargs
            return SimpleNamespace(
                text=json.dumps(
                    {
                        "explanation": "Giải thích hợp lệ.",
                        "suggestedActions": ["Rà soát lịch."],
                    }
                )
            )

        def close(self) -> None:
            captured["closed"] = True

    monkeypatch.setattr(llm.genai, "Client", FakeClient)

    result = llm._call_gemini(
        GeminiSettings(api_key="test-key", model="gemini-test", timeout_seconds=15),
        "solver evidence",
    )

    assert json.loads(result)["explanation"] == "Giải thích hợp lệ."
    assert captured["client"]["http_options"].timeout == 15000
    config = captured["request"]["config"]
    assert config.response_mime_type == "application/json"
    assert config.response_json_schema == llm.RESPONSE_SCHEMA
    assert captured["closed"] is True


def test_explain_returns_deterministic_fallback_without_api_key(monkeypatch) -> None:
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)

    response = client.post("/api/v1/scheduling/explain", json=explain_request())

    assert response.status_code == 200
    body = response.json()
    assert body["fallback"] is True
    assert body["usedModel"] is None
    assert "1000" in body["explanation"]
    assert body["suggestedActions"] == [
        "Rà soát nghỉ phép đã duyệt và nhu cầu nhân sự của ca bị ảnh hưởng."
    ]


def test_explain_falls_back_when_gemini_returns_invalid_json(monkeypatch) -> None:
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.setattr(llm, "_call_gemini", lambda settings, prompt: "not json")

    response = client.post("/api/v1/scheduling/explain", json=explain_request())

    assert response.status_code == 200
    assert response.json()["fallback"] is True


def test_explain_rejects_missing_solver_evidence() -> None:
    request = explain_request()
    del request["rules"]

    response = client.post("/api/v1/scheduling/explain", json=request)

    assert response.status_code == 422
