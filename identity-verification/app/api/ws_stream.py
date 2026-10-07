"""
WebSocket endpoint — receives live video frames from the upstream proxy.

Frame format (each WebSocket message):
    Binary  : raw JPEG / PNG bytes
    Text    : base64-encoded JPEG / PNG bytes

Per-frame response (JSON):
    {
      "frame": <int>,           // frame counter (1-based)
      "face_detected": <bool>,
      "match_score": <float>,   // this frame's cosine similarity vs ID (0 if no face)
      "avg_match_score": <float>,
      "blink_count": <int>,
      "ear": <float>,           // Eye Aspect Ratio (0 if no mesh)
      "laplacian_var": <float>, // face-crop sharpness
      "concluded": <bool>,      // True on the final verdict frame

      // Only when concluded=True:
      "liveness": "alive" | "spoof" | "unknown",
      "liveness_score": <float>,
      "success": <bool>,        // True only if identity AND liveness both pass
      "reason":  "verified" | "face_mismatch" | "spoof" | "uncertain"
    }

The server accumulates frames until STREAM_CONCLUDE_FRAMES valid face frames
have been seen, then finalises the verdict, sends it, actively CLOSES the socket,
and stops. Closing the socket ourselves (rather than waiting for the client) is
what prevents "orphan" frames — frames the client sends in the brief window
between the verdict being computed and the client learning about it. Once we
close, any late frame is rejected at the protocol level instead of reaching the
application and producing a spurious "verification not started" error.

Verification succeeds only when BOTH gates pass:
  1. Liveness — a real, live person is in front of the camera.
  2. Identity — that person's face matches the ID card (avg_match >= FACE_MATCH_THRESHOLD).
"""

import asyncio
import base64
import concurrent.futures
import logging
from typing import Optional

import cv2
import numpy as np
from fastapi import APIRouter
from fastapi.websockets import WebSocket, WebSocketDisconnect, WebSocketState

# Thread pool for CPU-bound ML inference (insightface, mediapipe).
# Running them in the event loop blocks ping/pong handling → keepalive timeout.
# max_workers=1 prevents concurrent insightface calls (not thread-safe).
_ml_executor = concurrent.futures.ThreadPoolExecutor(max_workers=1, thread_name_prefix="ml")

from app.config import settings
from app.core.models import SessionStatus
from app.core.session_manager import session_manager
from app.services.face_engine import (
    cosine_similarity,
    crop_face_region,
    get_live_frame_embedding,
)
from app.services.liveness_engine import analyze_frame, compute_verdict

ws_router = APIRouter(tags=["websocket"])
logger = logging.getLogger(__name__)

# Early-rejection thresholds (checked every frame after MIN_FRAMES_FOR_EARLY)
_EARLY_REJECT_FRAMES     = 8    # start checking early rejection after N face frames
_EARLY_REJECT_MATCH      = 0.35  # avg match this low → definitely wrong person
_EARLY_REJECT_LAPLACIAN  = 8.0   # face crop this blurry → likely printed photo
_MAX_TOTAL_FRAMES        = 150   # hard timeout: force verdict after N total frames (~30s at 5fps)


def _finalise(session, avg_match: float):
    """
    Produce the final (concluded) verdict once enough frames exist.

    Identity and liveness are two INDEPENDENT gates and BOTH must pass.
    A live person whose face does not match the ID (avg_match below
    FACE_MATCH_THRESHOLD) is NOT a successful verification — it is an
    identity mismatch and is reported as "unknown".
    """
    liveness_str, liveness_score = compute_verdict(session)

    # Identity gate: even a perfectly live face must match the ID card.
    if avg_match < settings.FACE_MATCH_THRESHOLD:
        logger.info(
            "Identity reject: avg_match=%.3f < threshold %.2f (liveness was %s)",
            avg_match, settings.FACE_MATCH_THRESHOLD, liveness_str,
        )
        return "unknown", liveness_score

    return liveness_str, liveness_score


def _decide_reason(liveness_str: str, avg_match: float):
    """
    Translate the raw verdict into a user-facing (success, reason) pair.

    reason values:
      "verified"      — identity + liveness both passed
      "face_mismatch" — live person, but not the one on the ID card
      "spoof"         — no live person detected (photo/screen)
      "uncertain"     — signals were inconclusive
    """
    if liveness_str == "alive" and avg_match >= settings.FACE_MATCH_THRESHOLD:
        return True, "verified"
    if avg_match < settings.FACE_MATCH_THRESHOLD:
        return False, "face_mismatch"
    if liveness_str == "spoof":
        return False, "spoof"
    return False, "uncertain"


def _check_verdict(session, avg_match: float):
    """
    Return (concluded, liveness_str, liveness_score).

    Two paths to conclusion:
    1. Normal: enough frames accumulated → full verdict.
    2. Early rejection: clear failure detected before hitting the frame target.
    """
    n = session.face_found_count
    total = session.frame_count

    # ── Normal conclusion ──────────────────────────────────────────────────────
    if n >= settings.STREAM_CONCLUDE_FRAMES:
        liveness_str, liveness_score = _finalise(session, avg_match)
        return True, liveness_str, liveness_score

    # ── Early rejection ────────────────────────────────────────────────────────
    if n >= _EARLY_REJECT_FRAMES:
        if avg_match < _EARLY_REJECT_MATCH:
            logger.info("Early reject: match=%.2f after %d face frames", avg_match, n)
            return True, "unknown", 0.0

        if session.laplacian_vars:
            median_lap = float(np.median(session.laplacian_vars))
            if median_lap < _EARLY_REJECT_LAPLACIAN:
                logger.info("Early reject: laplacian=%.1f (spoof)", median_lap)
                return True, "spoof", 0.1

    # ── Timeout: force verdict if too many total frames without conclusion ─────
    if total >= _MAX_TOTAL_FRAMES:
        liveness_str, liveness_score = _finalise(session, avg_match)
        logger.info("Timeout after %d frames: liveness=%s match=%.2f", total, liveness_str, avg_match)
        return True, liveness_str, liveness_score

    return False, "", 0.0


@ws_router.websocket("/ws/sessions/{session_id}/stream")
async def video_stream(websocket: WebSocket, session_id: str):
    """
    Accepts a continuous WebSocket stream of video frames, processes each one
    for face matching and liveness, and sends JSON feedback per frame.

    Closes automatically once STREAM_CONCLUDE_FRAMES valid frames are received.
    """
    await websocket.accept()

    session = session_manager.get(session_id)
    logger.info("WS %s: session=%s", session_id[:8], session)
    if session is None:
        logger.warning("WS %s: session not found", session_id[:8])
        await websocket.close(code=4404, reason="Session not found or expired")
        return

    emb = session.id_embedding
    logger.info("WS %s: embedding type=%s, status=%s", session_id[:8], type(emb).__name__, session.status)
    if emb is None:
        logger.warning("WS %s: id_embedding is None", session_id[:8])
        await websocket.close(code=4400, reason="Upload ID card before starting stream")
        return

    if session.status not in (SessionStatus.ID_UPLOADED, SessionStatus.STREAMING):
        logger.warning("WS %s: wrong status %s", session_id[:8], session.status)
        await websocket.close(
            code=4409, reason=f"Unexpected session status: {session.status}"
        )
        return

    session.status = SessionStatus.STREAMING
    logger.info("Session %s: WebSocket stream started", session_id)

    try:
        while True:
            msg = await websocket.receive()

            # ── Guard: session already concluded ──────────────────────────────
            # If a verdict was already issued (COMPLETED), any further frame is a
            # late/orphan frame the client sent before it learned we finished.
            # We ignore it silently — no error, no processing — instead of
            # replying "verification not started". Combined with the proactive
            # close below, this makes late frames a non-event.
            if session.status == SessionStatus.COMPLETED:
                continue

            # ── Decode incoming frame ─────────────────────────────────────────
            raw = _extract_bytes(msg)
            if raw is None:
                await websocket.send_json({"error": "Unrecognised message format"})
                continue

            frame = _decode_jpeg(raw)
            if frame is None:
                await websocket.send_json({"error": "Could not decode frame as image"})
                continue

            session.frame_count += 1
            loop = asyncio.get_event_loop()

            # ── Face detection + ArcFace (every 3rd frame, in thread pool) ───
            embedding, face_detected = None, False
            if session.frame_count % 3 == 1:
                embedding, face_detected = await loop.run_in_executor(
                    _ml_executor, get_live_frame_embedding, frame
                )

            match_score = 0.0
            if face_detected and embedding is not None:
                session.face_found_count += 1
                match_score = cosine_similarity(session.id_embedding, embedding)
                session.match_scores.append(match_score)
            elif session.match_scores:
                match_score = session.match_scores[-1]

            avg_match = float(np.mean(session.match_scores)) if session.match_scores else 0.0

            # ── Liveness analysis (mediapipe, in thread pool) ─────────────────
            face_crop: Optional[np.ndarray] = (
                crop_face_region(frame) if face_detected else None
            )
            _session = session   # capture for lambda
            _frame   = frame
            _crop    = face_crop
            signals = await loop.run_in_executor(
                _ml_executor, lambda: analyze_frame(_frame, _crop, _session)
            )

            # ── Build response ────────────────────────────────────────────────
            response: dict = {
                "frame":          session.frame_count,
                "face_detected":  face_detected,
                "match_score":    round(match_score, 3),
                "avg_match_score": round(avg_match, 3),
                "blink_count":    session.blink_count,
                "ear":            round(signals.ear, 3),
                "laplacian_var":  round(signals.laplacian_var, 1),
                "concluded":      False,
            }

            # ── Verdict check ─────────────────────────────────────────────────
            concluded, liveness_str, liveness_score = _check_verdict(session, avg_match)

            if concluded:
                success, reason = _decide_reason(liveness_str, avg_match)
                response["concluded"]      = True
                response["liveness"]       = liveness_str
                response["liveness_score"] = round(liveness_score, 3)
                response["success"]        = success
                response["reason"]         = reason
                session.status = SessionStatus.COMPLETED
                logger.info(
                    "Session %s: concluded — success=%s reason=%s liveness=%s(%.2f) "
                    "avg_match=%.3f blinks=%d frames=%d",
                    session_id, success, reason, liveness_str, liveness_score,
                    avg_match, session.blink_count, session.face_found_count,
                )
                await websocket.send_json(response)

                # ── Root-cause fix: close the socket ourselves right after the
                # verdict. This shuts the write side before the client can push
                # another frame, so no orphan frame is ever processed and no
                # spurious error is ever produced.
                try:
                    if websocket.client_state == WebSocketState.CONNECTED:
                        await websocket.close(code=1000, reason="verification_complete")
                except Exception:
                    pass  # client may have already gone away; nothing to do
                break

            await websocket.send_json(response)

    except WebSocketDisconnect:
        logger.info("Session %s: client disconnected", session_id)
    except Exception as exc:
        logger.error(
            "Session %s: unexpected error — %s", session_id, exc, exc_info=True
        )
        try:
            await websocket.send_json({"error": str(exc)})
        except Exception:
            pass


# ── Helpers ───────────────────────────────────────────────────────────────────

def _extract_bytes(msg: dict) -> Optional[bytes]:
    """Return raw bytes from a WebSocket message (binary or base64 text)."""
    if "bytes" in msg and msg["bytes"]:
        return msg["bytes"]
    if "text" in msg and msg["text"]:
        try:
            return base64.b64decode(msg["text"])
        except Exception:
            return None
    return None


def _decode_jpeg(raw: bytes) -> Optional[np.ndarray]:
    """Decode JPEG/PNG bytes to a BGR numpy array."""
    try:
        buf = np.frombuffer(raw, dtype=np.uint8)
        frame = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        return frame  # None if decoding fails
    except Exception:
        return None