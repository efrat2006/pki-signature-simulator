<#
.SYNOPSIS
    Install all IdentityVerification server dependencies into the project venv.

.DESCRIPTION
    Three packages (insightface, mediapipe, easyocr) each declare a *different*
    opencv variant as a pip dependency, which pip's strict resolver rejects as a
    conflict.  All three work fine at runtime with opencv-contrib-python (the
    superset), so we install it once and then install the three packages with
    --no-deps to skip the conflicting opencv declarations.

    Run from the project root:
        .\install.ps1
#>

$ErrorActionPreference = "Stop"
$pip = ".\venv\Scripts\pip.exe"

function Step($msg) {
    Write-Host "`n==> $msg" -ForegroundColor Cyan
}

# ── 0. Upgrade pip ────────────────────────────────────────────────────────────
Step "Upgrading pip"
& $pip install --upgrade pip

# ── 1. Core web framework ─────────────────────────────────────────────────────
Step "Installing FastAPI, uvicorn, pydantic"
& $pip install `
    "fastapi>=0.115.0" `
    "uvicorn[standard]>=0.30.0" `
    "python-multipart>=0.0.9" `
    "pydantic>=2.0.0"

# ── 2. Single opencv variant — satisfies insightface, mediapipe, easyocr ──────
Step "Installing opencv-contrib-python (shared by all ML packages)"
& $pip install "opencv-contrib-python>=4.8.0"

# ── 3. insightface deps, then insightface --no-deps ───────────────────────────
Step "Installing insightface runtime dependencies"
& $pip install `
    "numpy>=1.24.0" `
    "Pillow>=10.0.0" `
    "onnxruntime>=1.17.0" `
    "onnx>=1.14.0" `
    "scipy>=1.10.0" `
    "scikit-image>=0.21.0" `
    "tqdm>=4.30.0"

Step "Installing insightface (--no-deps to skip conflicting opencv-python)"
& $pip install "insightface>=0.7.3" --no-deps

# ── 4. mediapipe deps, then mediapipe --no-deps ───────────────────────────────
Step "Installing mediapipe runtime dependencies"
& $pip install `
    "absl-py>=2.0.0" `
    "flatbuffers>=23.5.26" `
    "sounddevice>=0.4.6" `
    "matplotlib>=3.7.0"

Step "Installing mediapipe (--no-deps to skip conflicting opencv-contrib-python)"
& $pip install "mediapipe>=0.10.0" --no-deps

# ── 5. easyocr deps (torch, etc.), then easyocr --no-deps ─────────────────────
Step "Installing PyTorch (CPU) and easyocr runtime dependencies"
& $pip install `
    "torch>=2.0.0" `
    "torchvision>=0.15.0" `
    "python-bidi>=0.4.2" `
    "PyYAML>=6.0" `
    "Shapely>=2.0.0" `
    "pyclipper>=1.3.0"

Step "Installing easyocr (--no-deps to skip conflicting opencv-python-headless)"
& $pip install "easyocr>=1.7.0" --no-deps

# ── Done ──────────────────────────────────────────────────────────────────────
Step "All dependencies installed successfully."
Write-Host @"

Start the server:
    .\venv\Scripts\python.exe main.py
  or
    .\venv\Scripts\uvicorn.exe main:app --host 0.0.0.0 --port 8000

API docs:  http://localhost:8000/docs

First-run model downloads (cached after the first use):
  insightface buffalo_l  →  ~/.insightface/models/   (~300 MB)
  EasyOCR he + en        →  ~/.EasyOCR/              (~100 MB)
"@
