import json
import logging

from google import genai
from google.genai import errors, types

from app.config import GeminiSettings
from app.schemas.explain import ExplainRequest, ExplainResponse

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You explain scheduling-solver results to a Vietnamese manager.
Use only the structured evidence supplied by the user. Do not invent causes,
diagnostics, employee facts, assignments, or scheduling decisions. Do not propose
specific employee-to-shift assignments. Return only a JSON object with exactly
these fields: explanation (a concise Vietnamese string) and suggestedActions (an
array of concise Vietnamese strings)."""

RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "explanation": {"type": "string"},
        "suggestedActions": {
            "type": "array",
            "items": {"type": "string"},
        },
    },
    "required": ["explanation", "suggestedActions"],
    "additionalProperties": False,
}


def _evidence_prompt(request: ExplainRequest) -> str:
    evidence = {
        "runId": request.run_id,
        "period": request.period.model_dump(by_alias=True, mode="json"),
        "status": request.status,
        "solverStatus": request.solver_status,
        "diagnostics": [
            diagnostic.model_dump(by_alias=True, mode="json")
            for diagnostic in request.diagnostics
        ],
        "statistics": request.statistics.model_dump(by_alias=True, mode="json"),
        "rules": request.rules.model_dump(by_alias=True, mode="json"),
        "totalPenalty": request.total_penalty,
        "penaltyDetails": request.penalty_details,
        "warnings": request.warnings,
        "errorMessage": request.error_message,
    }
    return json.dumps(evidence, ensure_ascii=False)


def _call_gemini(settings: GeminiSettings, prompt: str) -> str:
    client = genai.Client(
        api_key=settings.api_key,
        http_options=types.HttpOptions(timeout=int(settings.timeout_seconds * 1000)),
    )
    try:
        response = client.models.generate_content(
            model=settings.model,
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                temperature=0,
                max_output_tokens=400,
                response_mime_type="application/json",
                response_json_schema=RESPONSE_SCHEMA,
            ),
        )
        if not response.text:
            raise ValueError("Gemini returned an empty response")
        return response.text
    finally:
        client.close()


def _parse_llm_response(content: str) -> tuple[str, list[str]]:
    result = json.loads(content)
    explanation = result.get("explanation")
    actions = result.get("suggestedActions")
    if not isinstance(explanation, str) or not explanation.strip():
        raise ValueError("Gemini response is missing explanation")
    if not isinstance(actions, list) or not all(isinstance(action, str) for action in actions):
        raise ValueError("Gemini response has invalid suggestedActions")
    return explanation.strip(), [action.strip() for action in actions if action.strip()]


def _fallback_explanation(request: ExplainRequest) -> tuple[str, list[str]]:
    if request.status == "SUCCESS":
        explanation = "Trình tối ưu đã tìm được lịch khả thi"
        if request.solver_status == "OPTIMAL":
            explanation += " và xác nhận đây là kết quả tối ưu."
        else:
            explanation += ". Tính tối ưu chưa được chứng minh trong thời gian giới hạn."
        if request.total_penalty is not None:
            explanation += f" Tổng penalty là {request.total_penalty}."
        actions = ["Rà soát lịch trước khi gửi phê duyệt."]
        if request.warnings:
            actions.append("Xem lại các cảnh báo của solver trước khi phê duyệt.")
        return explanation, actions

    if request.status == "INFEASIBLE":
        messages = []
        actions = []
        for diagnostic in request.diagnostics:
            if diagnostic.code == "INSUFFICIENT_AVAILABLE_STAFF":
                shift = (
                    f" cho ca {diagnostic.work_shift_id}"
                    if diagnostic.work_shift_id is not None
                    else ""
                )
                messages.append(f"Không đủ nhân viên khả dụng{shift}.")
                actions.append("Rà soát nghỉ phép đã duyệt và nhu cầu nhân sự của ca bị ảnh hưởng.")
            elif diagnostic.code == "NO_FEASIBLE_SCHEDULE":
                messages.append("Không có lịch nào thỏa toàn bộ ràng buộc cứng.")
            else:
                messages.append(diagnostic.message)

        explanation = " ".join(messages) or "Không tìm được lịch khả thi theo dữ liệu solver trả về."
        if not actions:
            actions.append("Rà soát các diagnostics và điều chỉnh dữ liệu hoặc ràng buộc liên quan.")
        return explanation, list(dict.fromkeys(actions))

    explanation = request.error_message or "Solver không hoàn tất việc tạo lịch."
    return explanation, ["Kiểm tra trạng thái solver và thử lại sau khi xác minh dữ liệu đầu vào."]


def explain_schedule(request: ExplainRequest) -> ExplainResponse:
    settings = GeminiSettings.from_environment()
    if settings.api_key:
        try:
            explanation, actions = _parse_llm_response(
                _call_gemini(settings, _evidence_prompt(request))
            )
            return ExplainResponse(
                run_id=request.run_id,
                explanation=explanation,
                suggested_actions=actions,
                used_model=settings.model,
                fallback=False,
            )
        except (errors.APIError, ValueError) as exception:
            logger.warning("LLM explanation failed; returning fallback: %s", exception)

    explanation, actions = _fallback_explanation(request)
    return ExplainResponse(
        run_id=request.run_id,
        explanation=explanation,
        suggested_actions=actions,
        fallback=True,
    )
