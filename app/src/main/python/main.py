#!/usr/bin/env python3
import json
import sys
from pathlib import Path

# Add python root to sys.path
SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from core.dispatcher import CommandDispatcher
from core.protocol import MessageProtocol
from core.registry import ConverterRegistry


def main() -> None:
    # Ensure stdout/stdin are utf-8 and unbuffered if needed
    if sys.platform == "win32":
        try:
            sys.stdin.reconfigure(encoding="utf-8")
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    registry = ConverterRegistry()
    dispatcher = CommandDispatcher(registry)

    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                # Parent closed stdin or exited
                break

            line = line.strip()
            if not line:
                continue

            try:
                payload = json.loads(line)
                dispatcher.dispatch(payload)
            except json.JSONDecodeError as err:
                MessageProtocol.send(MessageProtocol.error(
                    "0",
                    f"Malformed JSON payload: {str(err)}",
                    code=400
                ))
        except (KeyboardInterrupt, SystemExit):
            break
        except Exception as e:
            MessageProtocol.send(MessageProtocol.error(
                "0",
                f"Unhandled exception in Python engine: {str(e)}",
                code=500
            ))


if __name__ == "__main__":
    main()
