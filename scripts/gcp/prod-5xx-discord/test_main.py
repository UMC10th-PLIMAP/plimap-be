import base64
import json
from urllib.error import HTTPError

import pytest

import main


TRACE_ID = "0123456789abcdef0123456789abcdef"


class CloudEvent:
    def __init__(self, log_entry):
        encoded = base64.b64encode(
            json.dumps(log_entry, ensure_ascii=False).encode("utf-8")
        ).decode("ascii")
        self.data = {"message": {"data": encoded}}


def log_entry(**payload_overrides):
    payload = {
        "event": "HTTP_5XX",
        "status": 500,
        "errorCode": "COMMON_500_INTERNAL_SERVER_ERROR",
        "method": "POST",
        "routeTemplate": "/api/v1/pins/{pinId}",
        "responseMessage": "서버 내부 오류가 발생했습니다.",
        "exceptionType": "java.lang.IllegalStateException",
        "traceId": TRACE_ID,
    }
    payload.update(payload_overrides)
    return {
        "insertId": "test-insert-id",
        "timestamp": "2026-09-25T12:34:56.123456Z",
        "resource": {
            "type": "cloud_run_revision",
            "labels": {"project_id": "plimap"},
        },
        "jsonPayload": payload,
    }


def field(payload, name):
    return next(
        item["value"]
        for item in payload["embeds"][0]["fields"]
        if item["name"] == name
    )


def test_pubsub_cloudevent를_LogEntry로_디코딩한다():
    expected = log_entry()

    actual = main.parse_pubsub_log_entry(CloudEvent(expected))

    assert actual == expected


def test_Discord_알림은_KST와_요청_진단_정보를_포함한다():
    payload = main.build_discord_payload(log_entry())

    assert payload["embeds"][0]["title"] == "🚨 [PROD] 백엔드 5xx 오류"
    assert field(payload, "발생 시간") == "2026-09-25 21:34:56 KST"
    assert field(payload, "요청") == "POST /api/v1/pins/{pinId}"
    assert field(payload, "상태") == "500"
    assert field(payload, "에러 코드") == "COMMON_500_INTERNAL_SERVER_ERROR"
    assert field(payload, "반환 메시지") == "서버 내부 오류가 발생했습니다."
    assert field(payload, "예외") == "IllegalStateException"
    assert field(payload, "Trace ID") == TRACE_ID
    assert "insertId%3D%22test-insert-id%22" in field(payload, "로그")
    assert payload["embeds"][0]["footer"]["text"] == "insertId: test-insert-id"
    assert payload["allowed_mentions"] == {"parse": []}


def test_예외_메시지와_스택_트레이스는_Discord에서_제외한다():
    entry = log_entry(
        exceptionMessage="discord에 노출되면 안 되는 원인",
        stack_trace="discord에 노출되면 안 되는 스택",
    )

    payload = main.build_discord_payload(entry)
    serialized = json.dumps(payload, ensure_ascii=False)

    assert "discord에 노출되면 안 되는 원인" not in serialized
    assert "discord에 노출되면 안 되는 스택" not in serialized
    assert "exceptionMessage" not in serialized
    assert "stack_trace" not in serialized


def test_반환_메시지는_Discord_필드_제한에서만_잘린다():
    response_message = "가" * 1_100

    payload = main.build_discord_payload(
        log_entry(responseMessage=response_message)
    )

    displayed = field(payload, "반환 메시지")
    assert len(displayed) == 1_024
    assert displayed.endswith("…")


def test_Trace_ID가_없으면_필드를_생략한다():
    entry = log_entry()
    del entry["jsonPayload"]["traceId"]

    payload = main.build_discord_payload(entry)

    assert all(
        item["name"] != "Trace ID"
        for item in payload["embeds"][0]["fields"]
    )


def test_테스트_이벤트는_제목으로_구분한다():
    payload = main.build_discord_payload(log_entry(testEvent=True))

    assert payload["embeds"][0]["title"] == "🧪 [TEST] 백엔드 5xx 알림"


def test_Webhook은_멘션이_차단된_JSON을_전송한다(monkeypatch):
    captured = {}

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def getcode(self):
            return 204

    def fake_urlopen(request, timeout):
        captured["request"] = request
        captured["timeout"] = timeout
        return Response()

    monkeypatch.setenv("DISCORD_WEBHOOK_URL", "https://discord.example/webhook")
    monkeypatch.setattr(main, "urlopen", fake_urlopen)

    main.forward_prod_5xx_to_discord(CloudEvent(log_entry()))

    sent = json.loads(captured["request"].data.decode("utf-8"))
    assert captured["timeout"] == 5
    assert captured["request"].get_header("User-agent") == main.DISCORD_USER_AGENT
    assert sent["allowed_mentions"] == {"parse": []}


def test_Discord_실패는_재시도_없이_함수_실패로_기록한다(monkeypatch):
    def fake_urlopen(_request, timeout):
        assert timeout == 5
        raise HTTPError(
            "https://discord.example/webhook",
            429,
            "rate limited",
            {},
            None,
        )

    monkeypatch.setenv("DISCORD_WEBHOOK_URL", "https://discord.example/webhook")
    monkeypatch.setattr(main, "urlopen", fake_urlopen)

    with pytest.raises(HTTPError):
        main.forward_prod_5xx_to_discord(CloudEvent(log_entry()))
