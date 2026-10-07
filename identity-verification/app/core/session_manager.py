import uuid
import time
import threading
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.core.models import IDCardData, SessionStatus


@dataclass
class SessionState:
    session_id: str
    created_at: float
    status: SessionStatus = SessionStatus.PENDING

    # ── From ID card ──────────────────────────────────────────────────────────
    id_embedding: Optional[np.ndarray] = None
    id_data: IDCardData = field(default_factory=IDCardData)

    # ── Accumulated from live video stream ────────────────────────────────────
    frame_count: int = 0
    face_found_count: int = 0
    match_scores: List[float] = field(default_factory=list)
    laplacian_vars: List[float] = field(default_factory=list)
    head_angles_history: List[Tuple[float, float, float]] = field(default_factory=list)

    # ── Blink tracking (stateful across frames) ───────────────────────────────
    blink_count: int = 0
    last_ear_below_threshold: bool = False
    ear_history: List[float] = field(default_factory=list)


class SessionManager:
    def __init__(self, ttl_seconds: int = 300, max_sessions: int = 200):
        self._sessions: Dict[str, SessionState] = {}
        self._lock = threading.Lock()
        self._ttl = ttl_seconds
        self._max = max_sessions

    def create(self) -> SessionState:
        with self._lock:
            self._evict_expired()
            if len(self._sessions) >= self._max:
                raise RuntimeError("Server is at capacity; try again later")
            sid = str(uuid.uuid4())
            session = SessionState(session_id=sid, created_at=time.time())
            self._sessions[sid] = session
            return session

    def get(self, session_id: str) -> Optional[SessionState]:
        with self._lock:
            session = self._sessions.get(session_id)
            if session is None:
                return None
            if time.time() - session.created_at > self._ttl:
                del self._sessions[session_id]
                return None
            return session

    def delete(self, session_id: str) -> None:
        with self._lock:
            self._sessions.pop(session_id, None)

    def _evict_expired(self) -> None:
        now = time.time()
        expired = [sid for sid, s in self._sessions.items() if now - s.created_at > self._ttl]
        for sid in expired:
            del self._sessions[sid]


# Singleton used across the application
session_manager = SessionManager()
