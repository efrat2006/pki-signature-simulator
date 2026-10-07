from dataclasses import dataclass, field
from typing import List


@dataclass
class Settings:
    # --- Face matching ---
    # insightface model pack name (downloaded automatically on first run)
    FACE_MODEL: str = "buffalo_l"
    # Cosine similarity threshold for face match (ArcFace normed embeddings).
    # Similarity is mapped to [0, 1]; values below this are a non-match.
    FACE_MATCH_THRESHOLD: float = 0.65
    # Minimum number of valid (face-found) frames required before issuing a match verdict
    MIN_MATCH_FRAMES: int = 5

    # --- Liveness detection ---
    # Minimum valid face frames required before computing liveness verdict
    MIN_LIVENESS_FRAMES: int = 30
    # EAR value below which an eye is considered "closed" (triggers blink detection)
    EAR_BLINK_THRESHOLD: float = 0.22
    # Minimum blinks detected to contribute positively to liveness score
    MIN_BLINKS_REQUIRED: int = 1
    # Minimum head-pose variance (degrees²) expected from a live person over the session
    HEAD_MOTION_VAR_THRESHOLD: float = 0.5
    # Face region Laplacian variance: below this suggests blurry print/screen replay
    LAPLACIAN_VAR_THRESHOLD: float = 50.0

    # --- OCR ---
    OCR_LANGUAGES: List[str] = field(default_factory=lambda: ["he", "en"])
    # Run OCR on GPU if available (set False for CPU-only servers)
    OCR_GPU: bool = False

    # --- Session management ---
    SESSION_TTL_SECONDS: int = 300   # 5 minutes
    MAX_SESSIONS: int = 200

    # --- Stream processing ---
    # Number of valid (face-found) frames required to finalise a streaming verdict
    STREAM_CONCLUDE_FRAMES: int = 20


settings = Settings()
