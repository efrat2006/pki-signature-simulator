"""
test_client.py — בדיקה ידנית של שרת ה-IdentityVerification

שימוש:
    # וידאו מקובץ + תמונות ת"ז מקובץ:
    python test_client.py --front id_front.jpg --back id_back.jpg --video test.mp4

    # מצלמה חיה (ברירת מחדל אם לא מציינים --video):
    python test_client.py --front id_front.jpg --back id_back.jpg

    # שרת על כתובת אחרת:
    python test_client.py --host 192.168.1.10 --port 8080 --front id_front.jpg
"""

import argparse
import asyncio
import base64
import json
import sys
import time

import cv2
import requests
import websockets

BASE = ""          # נקבע בזמן ריצה


# ── שלב 1: יצירת session ─────────────────────────────────────────────────────

def create_session() -> str:
    r = requests.post(f"{BASE}/api/v1/sessions")
    r.raise_for_status()
    data = r.json()
    sid = data["session_id"]
    print(f"[1] Session נוצר: {sid}")
    return sid


# ── שלב 2: העלאת תמונות ת"ז ─────────────────────────────────────────────────

def upload_id_card(session_id: str, front_path: str, back_path: str | None):
    files = {"front": open(front_path, "rb")}
    if back_path:
        files["back"] = open(back_path, "rb")

    r = requests.post(
        f"{BASE}/api/v1/sessions/{session_id}/id-card",
        files=files,
    )
    if r.status_code != 200:
        print(f"[!] שגיאה בהעלאת ת\"ז: {r.status_code} — {r.text}")
        sys.exit(1)

    data = r.json()
    print(f"[2] ת\"ז הועלתה. פנים זוהו: {data['face_detected']}")
    print(f"    OCR → שם פרטי: {data['id_data']['first_name']}")
    print(f"         שם משפחה: {data['id_data']['last_name']}")
    print(f"         תאריך לידה: {data['id_data']['date_of_birth']}")
    print(f"         מספר ת\"ז:  {data['id_data']['id_number']}")
    return data


# ── שלב 3: סטרימינג וידאו חי ─────────────────────────────────────────────────

async def stream_video(session_id: str, source):
    ws_url = BASE.replace("http://", "ws://").replace("https://", "wss://")
    uri = f"{ws_url}/ws/sessions/{session_id}/stream"

    cap = cv2.VideoCapture(source)
    if not cap.isOpened():
        print(f"[!] לא ניתן לפתוח מקור וידאו: {source}")
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 15
    delay = 1.0 / fps

    print(f"\n[3] מתחבר ל-WebSocket ושולח פריימים…")
    print(f"    (FPS: {fps:.0f} | מקור: {source})")
    print("-" * 60)

    async with websockets.connect(uri, max_size=10 * 1024 * 1024) as ws:
        frame_n = 0
        t0 = time.time()

        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                print("\n[3] סוף קובץ הוידאו / לא ניתן לקרוא פריים.")
                break

            # קידוד JPEG ושליחה כ-base64
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
            await ws.send(base64.b64encode(buf.tobytes()).decode())

            raw = await ws.recv()
            result = json.loads(raw)
            frame_n += 1

            if "error" in result:
                print(f"[!] שגיאת שרת: {result['error']}")
                break

            # הדפסת סטטוס תמציתי כל 5 פריימים
            if frame_n % 5 == 0 or result.get("concluded"):
                elapsed = time.time() - t0
                status = (
                    f"frame={result['frame']:>4} | "
                    f"face={'✓' if result['face_detected'] else '✗'} | "
                    f"match={result['avg_match_score']:.2f} | "
                    f"blinks={result['blink_count']} | "
                    f"EAR={result['ear']:.2f} | "
                    f"lap={result['laplacian_var']:.0f} | "
                    f"t={elapsed:.1f}s"
                )
                print(status)

            # תוצאה סופית
            if result.get("concluded"):
                print("\n" + "=" * 60)
                print("  תוצאת בדיקת חיות:")
                print(f"    סטטוס:  {result['liveness']}")
                print(f"    ציון:   {result['liveness_score']:.3f}")
                print(f"    ריצות עין: {result['blink_count']}")
                break

            # שמירת קצב שליחה תואם ל-FPS המקורי
            await asyncio.sleep(delay)

    cap.release()


# ── שלב 4: תוצאה מסכמת ──────────────────────────────────────────────────────

def get_final_result(session_id: str):
    r = requests.get(f"{BASE}/api/v1/sessions/{session_id}/result")
    r.raise_for_status()
    data = r.json()

    print("\n" + "=" * 60)
    print("  תוצאה סופית — GET /result")
    print(f"    סטטוס session:   {data['status']}")
    print(f"    חיות (liveness): {data['liveness']} (ציון {data['liveness_score']:.3f})")
    print(f"    התאמת פנים:      {data['match']} (ציון {data['match_score']:.3f})")
    print(f"    ריצות עין:       {data['blink_count']}")
    print(f"    פריימים שעובדו:  {data['frames_processed']}")
    print(f"    הודעה:           {data['message']}")
    print("=" * 60)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="בודק ידני לשרת IdentityVerification")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default=8000, type=int)
    parser.add_argument("--front", required=True, help="נתיב לתמונה קדמית של ת\"ז")
    parser.add_argument("--back",  default=None,  help="נתיב לתמונה אחורית של ת\"ז (אופציונלי)")
    parser.add_argument("--video", default=0,     help="נתיב לקובץ וידאו, או 0 למצלמה חיה")
    args = parser.parse_args()

    global BASE
    BASE = f"http://{args.host}:{args.port}"

    # source: מספר שלם לindex מצלמה, או נתיב קובץ
    source = int(args.video) if str(args.video).isdigit() else args.video

    session_id = create_session()
    upload_id_card(session_id, args.front, args.back)
    asyncio.run(stream_video(session_id, source))
    get_final_result(session_id)


if __name__ == "__main__":
    main()
