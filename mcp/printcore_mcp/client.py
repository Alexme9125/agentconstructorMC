"""HTTP client for a running PrintCore mod."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


class PrintCoreError(RuntimeError):
    def __init__(self, status: int, message: str):
        super().__init__(f"{status}: {message}")
        self.status = status
        self.message = message


class PrintCoreClient:
    def __init__(self, base: str | None = None, token: str | None = None, timeout: float = 30.0):
        self.base = (base or os.environ.get("PRINTCORE_URL") or "http://127.0.0.1:9471").rstrip("/")
        self.token = token if token is not None else os.environ.get("PRINTCORE_TOKEN", "printcore-dev-token")
        self.timeout = timeout

    def reachable(self) -> bool:
        try:
            self.status()
            return True
        except (PrintCoreError, OSError, TimeoutError, urllib.error.URLError):
            return False

    def status(self) -> dict[str, Any]:
        return self._json("GET", "/v1/status")

    def registry(self, prefix: str = "") -> dict[str, Any]:
        return self._json("GET", "/v1/registry/blocks", query={"prefix": prefix})

    def blocks(self, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, dimension: str = "minecraft:overworld") -> dict[str, Any]:
        return self._json(
            "GET",
            "/v1/world/blocks",
            query={"x1": x1, "y1": y1, "z1": z1, "x2": x2, "y2": y2, "z2": z2, "dimension": dimension},
        )

    def heightmap(self, x1: int, z1: int, x2: int, z2: int, dimension: str = "minecraft:overworld", type: str = "MOTION_BLOCKING_NO_LEAVES") -> dict[str, Any]:
        return self._json(
            "GET",
            "/v1/world/heightmap",
            query={"x1": x1, "z1": z1, "x2": x2, "z2": z2, "dimension": dimension, "type": type},
        )

    def upload_schematic(self, path: str, filename: str | None = None) -> dict[str, Any]:
        data = PathBytes.read(path)
        headers = {"X-Filename": filename or os.path.basename(path)}
        return self._json("POST", "/v1/schematics", raw=data, headers=headers)

    def submit_job(self, plan: dict[str, Any]) -> dict[str, Any]:
        return self._json("POST", "/v1/jobs", body=plan)

    def dry_run(self, job_id: str) -> dict[str, Any]:
        return self._json("POST", f"/v1/jobs/{job_id}/dry-run")

    def commit(self, job_id: str) -> dict[str, Any]:
        return self._json("POST", f"/v1/jobs/{job_id}/commit")

    def rollback(self, job_id: str) -> dict[str, Any]:
        return self._json("POST", f"/v1/jobs/{job_id}/rollback")

    def job(self, job_id: str) -> dict[str, Any]:
        return self._json("GET", f"/v1/jobs/{job_id}")

    def _json(
        self,
        method: str,
        path: str,
        query: dict[str, Any] | None = None,
        body: Any = None,
        raw: bytes | None = None,
        headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        url = self.base + path
        if query:
            url += "?" + urllib.parse.urlencode({k: v for k, v in query.items() if v is not None})
        data = raw
        req_headers = {"Authorization": f"Bearer {self.token}"}
        if headers:
            req_headers.update(headers)
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            req_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(url, data=data, method=method, headers=req_headers)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                payload = response.read().decode("utf-8")
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as exc:
            message = exc.read().decode("utf-8", errors="replace")
            try:
                parsed = json.loads(message)
                message = parsed.get("error", message)
            except json.JSONDecodeError:
                pass
            raise PrintCoreError(exc.code, message) from exc


class PathBytes:
    @staticmethod
    def read(path: str) -> bytes:
        with open(path, "rb") as handle:
            return handle.read()
