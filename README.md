# PKI Signature Simulator

A **PKI-based digital signature platform** with **biometric identity verification**, built as a final project. The system lets a verified user digitally sign PDF documents using certificates issued by a custom Certificate Authority, after proving their identity through real-time face-liveness detection and ID-card matching.

The project is composed of four independent components that communicate over HTTPS / WebSocket, behind an API gateway.

## Architecture

```
                    ┌─────────────────────┐
                    │   API Gateway        │
                    │   (Nginx + TLS)      │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                       │
┌───────▼────────┐   ┌─────────▼──────────┐  ┌─────────▼───────────┐
│  Desktop App   │   │   CA Server         │  │ Identity Verify     │
│  (JavaFX)      │◄─►│   (Spring Boot)     │  │ Service (FastAPI)   │
│  sign / verify │   │   issue / revoke    │  │ liveness + ID OCR   │
│  PDFs          │   │   certificates      │  │                     │
└────────────────┘   └─────────────────────┘  └─────────────────────┘
```

## Components

### 1. CA Server — Certificate Authority (`ca-server/`)
A **Spring Boot** REST service that acts as a private Certificate Authority.
- Issues and signs **X.509 certificates** from Certificate Signing Requests (CSR)
- Certificate revocation and **OCSP** (Online Certificate Status Protocol) responder
- **Ed25519** signing implemented behind a **Strategy pattern** (`SigningStrategy` interface)
- Identity assurance via security questions and OTP
- Uses **BouncyCastle** for cryptography and **PostgreSQL** (via Spring Data JPA) for storage

### 2. Desktop Client (`desktop/`)
A **JavaFX** desktop application for end users.
- User registration, login, and certificate enrollment (CSR generation)
- **Digital PDF signing and verification** using **Apache PDFBox**
- Real-time **face capture** (OpenCV) streamed to the identity service over WebSocket
- Certificate management and OCSP status checks
- Local persistence with **Hibernate** and a DAO layer

### 3. Identity Verification Service (`identity-verification/`)
A **Python / FastAPI** microservice for real-time biometric verification over WebSocket.
- **Passive liveness detection** (via MediaPipe): blink detection, head-pose micro-motion, and texture-sharpness analysis to reject photos and screen replays
- **Face matching** between the live camera feed and the ID-card photo
- **OCR** of Israeli ID cards (EasyOCR, Hebrew + English) to extract cardholder details

### 4. API Gateway (`api-gateway/`)
An **Nginx** reverse proxy providing a single TLS entry point and routing requests to the backend services.

## Tech Stack

| Layer | Technologies |
|-------|--------------|
| Certificate Authority - CA | Java, Spring Boot, Spring Data JPA, BouncyCastle, PostgreSQL |
| Desktop client | Java, JavaFX, Hibernate, Apache PDFBox, OpenCV |
| Identity verification | Python, FastAPI, MediaPipe, OpenCV, EasyOCR |
| Gateway | Nginx, TLS |
| Cryptography | X.509, CSR, OCSP, Ed25519 |

## Security Notes

This repository contains **source code only**. Private keys, certificates (`.p12`, `.pem`, `.key`), and configuration secrets are intentionally excluded via `.gitignore` and must be generated locally. Configuration files are provided as `.example` templates.

> This is an academic project built to explore PKI, digital signatures, and biometric identity verification. It is not intended for production use.
