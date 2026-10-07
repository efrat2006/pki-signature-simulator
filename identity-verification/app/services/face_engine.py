"""
Face detection and recognition via insightface (ArcFace over ONNX Runtime).

insightface's buffalo_l pack is downloaded automatically (~300 MB) into
~/.insightface/models/ on first use.  No GPU required; CPUExecutionProvider
is the default.
"""

import logging
from typing import Optional, Tuple

import cv2
import numpy as np

logger = logging.getLogger(__name__)

# Lazily initialised to avoid heavy import at startup
_face_app = None


def _get_app():
    global _face_app
    if _face_app is None:
        try:
            from insightface.app import FaceAnalysis
        except ImportError as exc:
            raise RuntimeError(
                "insightface is not installed. Run: pip install insightface onnxruntime"
            ) from exc
        logger.info("Loading insightface buffalo_l model…")
        _face_app = FaceAnalysis(
            name="buffalo_l",
            providers=["CPUExecutionProvider"],
        )
        _face_app.prepare(ctx_id=0, det_size=(320, 320))
        logger.info("insightface ready.")
    return _face_app


# ── Public API ────────────────────────────────────────────────────────────────

def extract_id_face_embedding(
    image: np.ndarray,
) -> Tuple[Optional[np.ndarray], bool]:
    """
    Detect faces in an ID card image and return the ArcFace embedding of the
    largest detected face.

    Returns
    -------
    (embedding, face_detected)
        embedding  – 512-dim normed float32 array, or None if no face.
        face_detected – True when a face was found.
    """
    app = _get_app()
    faces = app.get(image)
    if not faces:
        return None, False
    largest = _largest_face(faces)
    return np.array(largest.normed_embedding, dtype=np.float32), True


def get_live_frame_embedding(
    image: np.ndarray,
) -> Tuple[Optional[np.ndarray], bool]:
    """
    Detect and embed the largest face in a live video frame.

    Returns
    -------
    (embedding, face_detected)
    """
    app = _get_app()
    faces = app.get(image)
    if not faces:
        return None, False
    largest = _largest_face(faces)
    return np.array(largest.normed_embedding, dtype=np.float32), True


def crop_face_region(image: np.ndarray) -> Optional[np.ndarray]:
    """Return a BGR crop of the largest detected face, or None."""
    app = _get_app()
    faces = app.get(image)
    if not faces:
        return None
    f = _largest_face(faces)
    x1, y1, x2, y2 = (int(v) for v in f.bbox)
    h, w = image.shape[:2]
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)
    if x2 <= x1 or y2 <= y1:
        return None
    return image[y1:y2, x1:x2]


def cosine_similarity(emb1: np.ndarray, emb2: np.ndarray) -> float:
    """
    Cosine similarity between two ArcFace normed embeddings.

    ArcFace normed embeddings have unit L2 norm, so their dot product equals
    cosine similarity in [-1, 1].  We rescale to [0, 1] for convenience.
    """
    sim = float(np.dot(emb1, emb2))
    return (sim + 1.0) / 2.0


# ── Helpers ───────────────────────────────────────────────────────────────────

def _largest_face(faces):
    def _area(f):
        x1, y1, x2, y2 = f.bbox
        return (x2 - x1) * (y2 - y1)
    return max(faces, key=_area)
