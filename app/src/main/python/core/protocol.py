import json
import sys
from typing import Any, Dict, Optional


class MessageProtocol:
    """Handles structured JSON-RPC style communication over stdin/stdout."""

    @staticmethod
    def success(message_id: str, data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        return {
            "id": message_id,
            "status": "success",
            "data": data or {}
        }

    @staticmethod
    def error(message_id: str, error_message: str, code: int = 500) -> Dict[str, Any]:
        return {
            "id": message_id,
            "status": "error",
            "error": {
                "code": code,
                "message": error_message
            }
        }

    @staticmethod
    def progress(job_id: str, progress: float, stage: str = "") -> Dict[str, Any]:
        return {
            "type": "event",
            "event": "conversion_progress",
            "job_id": job_id,
            "progress": round(progress, 3),
            "stage": stage
        }

    @staticmethod
    def send(payload: Dict[str, Any]) -> None:
        """Flushes a single-line JSON string to stdout."""
        raw = json.dumps(payload, ensure_ascii=False)
        sys.stdout.write(raw + "\n")
        sys.stdout.flush()
