import logging
import pathlib

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from app.api.routes import router
from app.api.ws_stream import ws_router

_UI_PATH = pathlib.Path(__file__).parent / "test_ui.html"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s: %(message)s",
)

app = FastAPI(
    title="IdentityVerification",
    description=(
        "Real-time identity verification — liveness detection "
        "and ID card face matching over WebSocket."
    ),
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)
app.include_router(ws_router)


@app.get("/health", tags=["ops"])
async def health():
    return {"status": "ok"}


@app.get("/", include_in_schema=False)
async def test_ui():
    return FileResponse(_UI_PATH)


if __name__ == "__main__":
    import pathlib
    _base = pathlib.Path(__file__).parent
    _cert = _base / "cert.pem"
    _key  = _base / "key.pem"

    if not (_cert.exists() and _key.exists()):
        raise FileNotFoundError(
            "cert.pem / key.pem לא נמצאו. הרץ תחילה:\n"
            "  python generate_cert.py"
        )

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8443,
        ssl_certfile=str(_cert),
        ssl_keyfile=str(_key),
        reload=False,
        # Disable WebSocket keepalive pings — the ML thread pool blocks the
        # TCP receive buffer when the client sends frames faster than the server
        # can process them, causing pong frames to be delayed → 1011 timeout.
        ws_ping_interval=None,
        ws_ping_timeout=None,
    )
