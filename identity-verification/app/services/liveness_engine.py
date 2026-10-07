"""
Passive liveness detection using mediapipe Tasks API (v0.10+).

Three complementary signals accumulated over the video stream:

1. Blink detection via face blendshapes
   FaceLandmarker outputs eyeBlinkLeft / eyeBlinkRight scores (0=open, 1=closed).
   A live person blinks naturally; a static photo/screen replay does not.

2. Head-pose micro-motion (solvePnP)
   Natural involuntary micro-movements produce variance in pitch/yaw/roll.
   A printed photo held still shows near-zero variance.

3. Face-crop texture sharpness (Laplacian variance)
   Real faces at close range are sharp; printed/screen replays are blurrier
   or show characteristic moiré patterns.
"""

import logging
import pathlib
import urllib.request
from dataclasses import dataclass
from typing import Optional, Tuple

import cv2
import numpy as np

logger = logging.getLogger(__name__)

# ── Model path ────────────────────────────────────────────────────────────────
_MODEL_PATH = pathlib.Path(__file__).parent.parent.parent / "face_landmarker.task"
_MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "face_landmarker/face_landmarker/float16/1/face_landmarker.task"
)

# Sparse 3-D face reference points (mm) matching mediapipe landmark indices below
_FACE_3D = np.array([
    (0.0,    0.0,    0.0),      # landmark 1  — nose tip
    (0.0,  -330.0, -65.0),     # landmark 152 — chin
    (-225.0, 170.0, -135.0),   # landmark 226 — left eye left corner
    (225.0,  170.0, -135.0),   # landmark 446 — right eye right corner
    (-150.0, -150.0, -125.0),  # landmark 57  — left mouth corner
    (150.0,  -150.0, -125.0),  # landmark 287 — right mouth corner
], dtype=np.float64)
_POSE_LM_IDX = [1, 152, 226, 446, 57, 287]

# EAR landmark indices (same topology as old FaceMesh)
_LEFT_EYE  = [362, 385, 387, 263, 373, 380]
_RIGHT_EYE = [33,  160, 158, 133, 153, 144]

# Lazily created landmarker
_landmarker = None


def _ensure_model() -> None:
    if not _MODEL_PATH.exists():
        logger.info("Downloading face_landmarker.task (~3.8 MB)…")
        urllib.request.urlretrieve(_MODEL_URL, _MODEL_PATH)
        logger.info("face_landmarker.task downloaded.")


def _get_landmarker():
    global _landmarker
    if _landmarker is None:
        _ensure_model()
        from mediapipe.tasks.python import vision as mp_vision
        from mediapipe.tasks.python.core.base_options import BaseOptions

        options = mp_vision.FaceLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(_MODEL_PATH)),
            running_mode=mp_vision.RunningMode.IMAGE,
            num_faces=1,
            output_face_blendshapes=True,
            min_face_detection_confidence=0.5,
            min_face_presence_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        _landmarker = mp_vision.FaceLandmarker.create_from_options(options)
        logger.info("mediapipe FaceLandmarker ready.")
    return _landmarker


# ── Per-frame signals ─────────────────────────────────────────────────────────

@dataclass
class LivenessSignals:
    mesh_found: bool = False
    ear: float = 0.0                          # Eye Aspect Ratio (displayed in UI)
    blink_score: float = 0.0                  # blendshape blink score this frame
    blinked_this_frame: bool = False
    laplacian_var: float = 0.0
    head_angles: Optional[Tuple[float, float, float]] = None


def analyze_frame(
    frame_bgr: np.ndarray,
    face_crop_bgr: Optional[np.ndarray],
    session,
) -> LivenessSignals:
    """
    Analyse one video frame and update session liveness accumulators in place.
    """
    signals = LivenessSignals()
    h, w = frame_bgr.shape[:2]

    import mediapipe as mp
    rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

    result = _get_landmarker().detect(mp_img)
    if not result.face_landmarks:
        return signals

    lm = result.face_landmarks[0]   # list of NormalizedLandmark
    signals.mesh_found = True

    # ── 1. Blink detection — blendshapes + EAR fallback ──────────────────────
    BLINK_BS_THRESH  = 0.20   # blendshape: 0=open, 1=closed; lower = catches quick blinks
    EAR_BLINK_THRESH = 0.22   # EAR: lower = eye more closed

    # Blendshape-based signal
    blink_by_bs = False
    if result.face_blendshapes:
        bs_map = {b.category_name: b.score for b in result.face_blendshapes[0]}
        left  = bs_map.get("eyeBlinkLeft",  0.0)
        right = bs_map.get("eyeBlinkRight", 0.0)
        blink_score = (left + right) / 2.0
        signals.blink_score = blink_score
        blink_by_bs = blink_score > BLINK_BS_THRESH

    # EAR-based signal (fallback / confirmation)
    ear = _eye_aspect_ratio(lm, _LEFT_EYE, _RIGHT_EYE, w, h)
    signals.ear = ear
    session.ear_history.append(ear)
    blink_by_ear = ear < EAR_BLINK_THRESH

    # Count blink when either signal says "closed" → then "open" again
    eye_closed = blink_by_bs or blink_by_ear
    if eye_closed:
        session.last_ear_below_threshold = True
    elif session.last_ear_below_threshold:
        session.blink_count += 1
        signals.blinked_this_frame = True
        session.last_ear_below_threshold = False

    # ── 2. Head pose ──────────────────────────────────────────────────────────
    angles = _head_pose(lm, w, h)
    signals.head_angles = angles
    if angles is not None:
        session.head_angles_history.append(angles)

    # ── 3. Texture / sharpness ────────────────────────────────────────────────
    if face_crop_bgr is not None and face_crop_bgr.size > 0:
        gray = cv2.cvtColor(face_crop_bgr, cv2.COLOR_BGR2GRAY)
        lap_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        signals.laplacian_var = lap_var
        session.laplacian_vars.append(lap_var)

    return signals


# ── Final verdict ─────────────────────────────────────────────────────────────

def compute_verdict(session) -> Tuple[str, float]:
    """Return (status, score): status is 'alive' | 'spoof' | 'unknown'."""
    if session.face_found_count < 10:
        return "unknown", 0.0

    components = []  # (name, score, weight)

    # Blink
    blink_score = 1.0 if session.blink_count >= 1 else 0.0
    components.append(("blink", blink_score, 0.40))

    # Head micro-motion
    if len(session.head_angles_history) >= 5:
        angles = np.array(session.head_angles_history, dtype=np.float64)
        variance = float(np.mean(np.var(angles, axis=0)))
        motion_score = min(1.0, variance / 2.0)
    else:
        motion_score = 0.0
    components.append(("motion", motion_score, 0.30))

    # Texture sharpness
    if session.laplacian_vars:
        median_lap = float(np.median(session.laplacian_vars))
        texture_score = min(1.0, median_lap / 100.0)
    else:
        texture_score = 0.5
    components.append(("texture", texture_score, 0.30))

    total_w = sum(w for _, _, w in components)
    score = sum(s * w for _, s, w in components) / total_w

    logger.debug("Liveness %s → %.3f", {n: round(s, 2) for n, s, _ in components}, score)

    if score >= 0.55:
        return "alive", score
    if score <= 0.35:
        return "spoof", score
    return "unknown", score


# ── Helpers ───────────────────────────────────────────────────────────────────

def _eye_aspect_ratio(lm, left_idx, right_idx, img_w, img_h) -> float:
    def _ear(indices):
        pts = [(lm[i].x * img_w, lm[i].y * img_h) for i in indices]
        A = np.linalg.norm(np.subtract(pts[1], pts[5]))
        B = np.linalg.norm(np.subtract(pts[2], pts[4]))
        C = np.linalg.norm(np.subtract(pts[0], pts[3]))
        return float((A + B) / (2.0 * C)) if C > 1e-6 else 0.0
    return (_ear(left_idx) + _ear(right_idx)) / 2.0


def _head_pose(lm, img_w: int, img_h: int) -> Optional[Tuple[float, float, float]]:
    img_pts = np.array(
        [(lm[i].x * img_w, lm[i].y * img_h) for i in _POSE_LM_IDX],
        dtype=np.float64,
    )
    focal = float(img_w)
    cam = np.array(
        [[focal, 0.0, img_w / 2.0],
         [0.0, focal, img_h / 2.0],
         [0.0, 0.0,   1.0]],
        dtype=np.float64,
    )
    ok, rvec, _ = cv2.solvePnP(
        _FACE_3D, img_pts, cam, np.zeros((4, 1)), flags=cv2.SOLVEPNP_ITERATIVE
    )
    if not ok:
        return None
    rmat, _ = cv2.Rodrigues(rvec)
    sy = np.sqrt(rmat[0, 0] ** 2 + rmat[1, 0] ** 2)
    pitch = float(np.degrees(np.arctan2(-rmat[2, 0], sy)))
    yaw   = float(np.degrees(np.arctan2(rmat[1, 0],  rmat[0, 0])))
    roll  = float(np.degrees(np.arctan2(rmat[2, 1],  rmat[2, 2])))
    return pitch, yaw, roll
