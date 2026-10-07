"""
REST endpoints for the identity-verification flow.

Typical call sequence
─────────────────────
1. POST  /api/v1/sessions                       → create session, get session_id
2. POST  /api/v1/sessions/{id}/id-card          → upload ID images (multipart)
3. WS    /ws/sessions/{id}/stream               → stream live video frames
4. GET   /api/v1/sessions/{id}/result           → poll or retrieve final verdict
"""

import logging
from typing import Optional

import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from app.config import settings
from app.core.models import (
    CreateSessionResponse,
    IDCardData,
    IDUploadResponse,
    LivenessStatus,
    MatchStatus,
    SessionStatus,
    VerificationResult,
)
from app.core.session_manager import session_manager
from app.services.face_engine import extract_id_face_embedding
from app.services.liveness_engine import compute_verdict
from app.services.ocr_engine import process_id_card

router = APIRouter(prefix="/api/v1", tags=["verification"])
logger = logging.getLogger(__name__)


@router.post("/debug/ocr", tags=["debug"])
async def debug_ocr(
    front: UploadFile = File(...),
    back: Optional[UploadFile] = File(default=None),
):
    """
    Debug endpoint: returns raw Tesseract lines (text, Y position) from the ID card.
    Use this to diagnose OCR accuracy on a specific card.
    """
    from app.services.ocr_engine import _run_tesseract, _preprocess_for_tess, _tesseract_available
    front_img = _decode_upload(await front.read(), "front")
    back_img  = _decode_upload(await back.read(), "back") if back else None

    result = {}
    result["tesseract_available"] = _tesseract_available()

    if result["tesseract_available"]:
        lines = _run_tesseract(front_img)
        result["front_lines"] = [{"text": t, "y": y} for t, y in lines]
        if back_img is not None:
            result["back_lines"]  = [{"text": t, "y": y} for t, y in _run_tesseract(back_img)]

    from app.services.ocr_engine import process_id_card
    parsed = process_id_card(front_img, back_img)
    result["parsed"] = parsed.model_dump()
    return result


# ── Helpers ───────────────────────────────────────────────────────────────────

def _decode_upload(data: bytes, field_name: str) -> np.ndarray:
    buf = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
    if img is None:
        raise HTTPException(status_code=400, detail=f"'{field_name}' is not a valid image")
    return img


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/sessions", response_model=CreateSessionResponse, status_code=201)
async def create_session():
    """
    Create a new identity-verification session.

    Returns a session_id that must be included in all subsequent requests.
    """
    try:
        session = session_manager.create()
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    return CreateSessionResponse(
        session_id=session.session_id,
        status=session.status,
        created_at=session.created_at,
    )


@router.post(
    "/sessions/{session_id}/id-card",
    response_model=IDUploadResponse,
)
async def upload_id_card(
    session_id: str,
    front: UploadFile = File(..., description="Front side of the ID card (JPEG/PNG)"),
    back: Optional[UploadFile] = File(
        default=None, description="Back side of the ID card (optional)"
    ),
):
    """
    Upload the ID card images.

    Extracts:
    - Face embedding (ArcFace) used later for person matching
    - OCR fields: first name, last name, date of birth, ID number

    The session transitions from PENDING → ID_UPLOADED on success.
    """
    session = session_manager.get(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found or expired")
    if session.status != SessionStatus.PENDING:
        raise HTTPException(
            status_code=409,
            detail=f"ID card already uploaded (session status: {session.status})",
        )

    front_img = _decode_upload(await front.read(), "front")
    back_img  = _decode_upload(await back.read(), "back") if back else None

    # Face embedding from ID photo
    embedding, face_detected = extract_id_face_embedding(front_img)
    if not face_detected:
        raise HTTPException(
            status_code=422,
            detail="No face detected in the front ID image. "
                   "Ensure the image is clear and the face is visible.",
        )

    session.id_embedding = embedding

    # OCR
    session.id_data = process_id_card(
        front_img, back_img, gpu=settings.OCR_GPU
    )

    session.status = SessionStatus.ID_UPLOADED
    logger.info(
        "Session %s: ID uploaded. OCR → %s", session_id, session.id_data
    )

    return IDUploadResponse(
        session_id=session_id,
        status=session.status,
        id_data=session.id_data,
        face_detected=True,
    )


@router.get("/sessions/{session_id}/result", response_model=VerificationResult)
async def get_result(session_id: str):
    """
    Return the current (or final) verification result for a session.

    Can be polled while the WebSocket stream is active, or called once after
    the WebSocket closes (status will be COMPLETED).
    """
    session = session_manager.get(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found or expired")

    # Liveness verdict
    liveness_str, liveness_score = compute_verdict(session)
    liveness = LivenessStatus(liveness_str)

    # Match verdict
    if session.match_scores:
        avg_sim = float(np.mean(session.match_scores))
    else:
        avg_sim = 0.0

    enough_frames = len(session.match_scores) >= settings.MIN_MATCH_FRAMES
    if enough_frames:
        match = (
            MatchStatus.MATCH
            if avg_sim >= settings.FACE_MATCH_THRESHOLD
            else MatchStatus.NO_MATCH
        )
    else:
        match = MatchStatus.UNKNOWN

    # Finalise session status when both verdicts are available
    if (
        liveness != LivenessStatus.UNKNOWN
        and match != MatchStatus.UNKNOWN
        and session.status == SessionStatus.STREAMING
    ):
        session.status = SessionStatus.COMPLETED

    message = _build_message(liveness, match, session)

    return VerificationResult(
        session_id=session_id,
        status=session.status,
        liveness=liveness,
        match=match,
        liveness_score=liveness_score,
        match_score=avg_sim,
        blink_count=session.blink_count,
        frames_processed=session.frame_count,
        id_data=session.id_data,
        message=message,
    )


def _build_message(
    liveness: LivenessStatus, match: MatchStatus, session
) -> str:
    if session.status in (SessionStatus.PENDING, SessionStatus.ID_UPLOADED):
        return "Waiting for live video stream"
    if liveness == LivenessStatus.SPOOF:
        return "Liveness check failed: potential spoofing detected"
    if liveness == LivenessStatus.UNKNOWN:
        return f"Liveness check in progress ({session.frame_count} frames collected)"
    if match == MatchStatus.NO_MATCH:
        return "Face does not match the ID card photo"
    if match == MatchStatus.UNKNOWN:
        return "Face matching in progress"
    return "Identity verified successfully"
