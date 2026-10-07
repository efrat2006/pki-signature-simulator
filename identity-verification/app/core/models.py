from enum import Enum
from typing import Optional
from pydantic import BaseModel


class SessionStatus(str, Enum):
    PENDING = "pending"           # Created, waiting for ID card upload
    ID_UPLOADED = "id_uploaded"   # ID processed; ready for live video stream
    STREAMING = "streaming"       # Actively receiving video frames
    COMPLETED = "completed"       # Final verdict reached
    FAILED = "failed"


class LivenessStatus(str, Enum):
    UNKNOWN = "unknown"   # Not enough frames yet
    ALIVE = "alive"
    SPOOF = "spoof"


class MatchStatus(str, Enum):
    UNKNOWN = "unknown"
    MATCH = "match"
    NO_MATCH = "no_match"


class IDCardData(BaseModel):
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    date_of_birth: Optional[str] = None


# ── REST response schemas ─────────────────────────────────────────────────────

class CreateSessionResponse(BaseModel):
    session_id: str
    status: SessionStatus
    created_at: float


class IDUploadResponse(BaseModel):
    session_id: str
    status: SessionStatus
    id_data: IDCardData
    face_detected: bool


class VerificationResult(BaseModel):
    session_id: str
    status: SessionStatus
    liveness: LivenessStatus
    match: MatchStatus
    liveness_score: float   # 0–1
    match_score: float      # 0–1 (cosine similarity, averaged over stream)
    blink_count: int
    frames_processed: int
    id_data: IDCardData
    message: str


# ── WebSocket per-frame response ──────────────────────────────────────────────

class FrameResponse(BaseModel):
    frame: int
    face_detected: bool
    match_score: float       # this frame's similarity (0 if no face)
    avg_match_score: float   # running average
    blink_count: int
    ear: float               # eye aspect ratio (0 if no mesh)
    laplacian_var: float     # face-region sharpness
    concluded: bool          # True on the final frame only

    # Only present when concluded=True
    liveness: Optional[str] = None
    liveness_score: Optional[float] = None
