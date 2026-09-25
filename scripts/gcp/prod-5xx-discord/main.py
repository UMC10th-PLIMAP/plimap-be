import base64
import json
import logging
import os
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

import functions_framework


LOGGER = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")
DISCORD_FIELD_LIMIT = 1_024
DISCORD_FOOTER_LIMIT = 2_048
WEBHOOK_TIMEOUT_SECONDS = 5
DEFAULT_PROJECT_ID = "plimap"


@functions_framework.cloud_event
def forward_prod_5xx_to_discord(cloud_event):
    insert_id = "<unknown>"
    try:
        log_entry = parse_pubsub_log_entry(cloud_event)
        insert_id = required_text(log_entry, "insertId")
        payload = build_discord_payload(log_entry)
    except (KeyError, TypeError, ValueError) as exception:
        LOGGER.error(
            "Invalid HTTP_5XX log event; insertId=%s errorType=%s",
            insert_id,
            type(exception).__name__,
        )
        return

    try:
        send_discord_webhook(payload)
    except (HTTPError, URLError, TimeoutError, RuntimeError) as exception:
        LOGGER.error(
            "Discord webhook delivery failed; insertId=%s errorType=%s",
            insert_id,
            type(exception).__name__,
        )
        raise


def parse_pubsub_log_entry(cloud_event):
    message = cloud_event.data["message"]
    encoded_data = required_text(message, "data")
    decoded_data = base64.b64decode(encoded_data, validate=True).decode("utf-8")
    log_entry = json.loads(decoded_data)
    if not isinstance(log_entry, dict):
        raise TypeError("Log entry must be a JSON object.")
    return log_entry


def build_discord_payload(log_entry):
    json_payload = log_entry.get("jsonPayload")
    if not isinstance(json_payload, dict):
        raise TypeError("jsonPayload must be a JSON object.")
    if required_text(json_payload, "event") != "HTTP_5XX":
        raise ValueError("Unsupported log event.")

    status = int(json_payload["status"])
    if status < 500 or status >= 600:
        raise ValueError("HTTP status must be in the 5xx range.")

    insert_id = required_text(log_entry, "insertId")
    project_id = resolve_project_id(log_entry)
    logs_url = build_logs_explorer_url(project_id, insert_id)
    test_event = json_payload.get("testEvent") is True

    fields = [
        discord_field("발생 시간", format_kst(required_text(log_entry, "timestamp"))),
        discord_field("요청", request_summary(json_payload)),
        discord_field("상태", str(status), inline=True),
        discord_field("에러 코드", required_text(json_payload, "errorCode"), inline=True),
        discord_field(
            "반환 메시지",
            required_text(json_payload, "responseMessage"),
        ),
        discord_field(
            "예외",
            simple_exception_name(required_text(json_payload, "exceptionType")),
        ),
    ]

    trace_id = optional_text(json_payload.get("traceId"))
    if trace_id is not None:
        fields.append(discord_field("Trace ID", trace_id))
    fields.append(
        discord_field("로그", f"[GCP 로그에서 상세 보기]({logs_url})")
    )

    return {
        "username": "PLIMAP 운영 알림",
        "allowed_mentions": {"parse": []},
        "embeds": [
            {
                "title": (
                    "🧪 [TEST] 백엔드 5xx 알림"
                    if test_event
                    else "🚨 [PROD] 백엔드 5xx 오류"
                ),
                "color": 0xF1C40F if test_event else 0xE74C3C,
                "fields": fields,
                "footer": {
                    "text": truncate(f"insertId: {insert_id}", DISCORD_FOOTER_LIMIT)
                },
            }
        ],
    }


def send_discord_webhook(payload):
    webhook_url = os.environ.get("DISCORD_WEBHOOK_URL")
    if webhook_url is None or webhook_url.isspace():
        raise RuntimeError("Discord webhook secret is not configured.")

    request = Request(
        webhook_url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=WEBHOOK_TIMEOUT_SECONDS) as response:
        status = response.getcode()
        if status < 200 or status >= 300:
            raise RuntimeError(f"Discord webhook returned HTTP {status}.")


def format_kst(timestamp):
    normalized = timestamp[:-1] + "+00:00" if timestamp.endswith("Z") else timestamp
    occurred_at = datetime.fromisoformat(normalized)
    if occurred_at.tzinfo is None:
        occurred_at = occurred_at.replace(tzinfo=timezone.utc)
    return occurred_at.astimezone(KST).strftime("%Y-%m-%d %H:%M:%S KST")


def request_summary(json_payload):
    method = required_text(json_payload, "method")
    route_template = required_text(json_payload, "routeTemplate")
    return f"{method} {route_template}"


def simple_exception_name(exception_type):
    return exception_type.rsplit(".", maxsplit=1)[-1]


def resolve_project_id(log_entry):
    resource = log_entry.get("resource")
    if isinstance(resource, dict):
        labels = resource.get("labels")
        if isinstance(labels, dict):
            project_id = optional_text(labels.get("project_id"))
            if project_id is not None:
                return project_id

    project_id = optional_text(os.environ.get("GOOGLE_CLOUD_PROJECT"))
    return project_id if project_id is not None else DEFAULT_PROJECT_ID


def build_logs_explorer_url(project_id, insert_id):
    query = quote(f'insertId="{insert_id}"', safe="")
    project = quote(project_id, safe="")
    return (
        "https://console.cloud.google.com/logs/query"
        f";query={query};duration=PT1H?project={project}"
    )


def discord_field(name, value, inline=False):
    return {
        "name": name,
        "value": truncate(str(value), DISCORD_FIELD_LIMIT),
        "inline": inline,
    }


def truncate(value, limit):
    if len(value) <= limit:
        return value
    return value[: limit - 1] + "…"


def required_text(container, key):
    value = optional_text(container.get(key))
    if value is None:
        raise ValueError(f"{key} is required.")
    return value


def optional_text(value):
    if value is None:
        return None
    text = str(value)
    return text if not text.isspace() and text != "" else None
