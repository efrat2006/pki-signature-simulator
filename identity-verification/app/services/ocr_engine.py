"""
OCR for Israeli identity cards (תעודת זהות).

Uses EasyOCR with Hebrew + English language models (downloaded automatically
on first use, ~100 MB into ~/.EasyOCR/).

Field extraction is heuristic: the parser looks for known Hebrew/English
label strings on the front of the card, then picks the next text block as
the field value.  Accuracy depends on image quality and card generation;
caller should treat results as best-effort and display them for human review.
"""

import logging
import pathlib
import re
from typing import List, Optional, Tuple

import cv2
import numpy as np

from app.core.models import IDCardData

logger = logging.getLogger(__name__)

_OcrResult = List[Tuple]
_easyocr_reader = None


def _get_easyocr(gpu: bool = False):
    global _easyocr_reader
    if _easyocr_reader is None:
        import easyocr
        logger.info("Loading EasyOCR (en)…")
        _easyocr_reader = easyocr.Reader(["en"], gpu=gpu, verbose=False)
        logger.info("EasyOCR ready.")
    return _easyocr_reader


_TESS_EXE      = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
_TESSDATA_DIR  = str(pathlib.Path(__file__).parent.parent.parent / "tessdata")


def _tesseract_available() -> bool:
    try:
        import pytesseract
        pytesseract.pytesseract.tesseract_cmd = _TESS_EXE
        pytesseract.get_tesseract_version()
        heb = pathlib.Path(_TESSDATA_DIR) / "heb.traineddata"
        return heb.exists()
    except Exception:
        return False


def _preprocess_for_tess(img: np.ndarray) -> np.ndarray:
    """Scale up and binarise for better Tesseract accuracy."""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    # Scale up 2× — improves small-text recognition significantly
    h, w = gray.shape
    gray = cv2.resize(gray, (w * 2, h * 2), interpolation=cv2.INTER_CUBIC)
    # Otsu binarisation
    _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return binary


def _run_tesseract(img: np.ndarray):
    """
    Run Tesseract and return list of (text, top_y) tuples sorted top→bottom.
    Using image_to_data gives us Y position so we can correctly match
    label → value even when both labels OCR to the same proximity.
    """
    import os
    import pytesseract
    from PIL import Image as PILImage
    pytesseract.pytesseract.tesseract_cmd = _TESS_EXE
    os.environ["TESSDATA_PREFIX"] = _TESSDATA_DIR
    processed = _preprocess_for_tess(img)
    pil = PILImage.fromarray(processed)

    data = pytesseract.image_to_data(
        pil, lang="heb+eng", config="--psm 4",
        output_type=pytesseract.Output.DICT
    )

    # Group by line_num, collect (line_num, top_y, joined_text)
    lines: dict = {}
    for i, text in enumerate(data["text"]):
        text = text.strip()
        if not text or int(data["conf"][i]) < 10:
            continue
        line_num = data["line_num"][i]
        top      = data["top"][i]
        if line_num not in lines:
            lines[line_num] = {"top": top, "words": []}
        lines[line_num]["words"].append(text)

    return sorted(
        [((" ".join(v["words"])), v["top"]) for v in lines.values()],
        key=lambda r: r[1]   # sort by Y position
    )


# ── Public API ────────────────────────────────────────────────────────────────

def process_id_card(
    front: np.ndarray,
    back: Optional[np.ndarray] = None,
    gpu: bool = False,
) -> IDCardData:
    """
    Two-engine strategy:
      1. Tesseract (heb+eng) — reads Hebrew names directly from the front.
      2. EasyOCR (en)        — reads numeric fields and MRZ from the back.
    Falls back gracefully if Tesseract is not installed.
    """
    try:
        tess_ok = _tesseract_available()

        # ── Numeric fields + MRZ via EasyOCR ─────────────────────────────────
        reader = _get_easyocr(gpu=gpu)
        easy_results: _OcrResult = reader.readtext(front, detail=1)
        if back is not None:
            easy_results += reader.readtext(back, detail=1)
        data = _parse_fields(easy_results)

        # ── Hebrew names via Tesseract (if installed) ─────────────────────────
        if tess_ok and (not data.first_name or not data.last_name):
            tess_lines = _run_tesseract(front)
            if back is not None:
                # merge back lines, offset Y so they sort after front
                max_y = max((y for _, y in tess_lines), default=0)
                tess_lines += [(t, y + max_y + 100) for t, y in _run_tesseract(back)]
            _extract_hebrew_names(tess_lines, data)

        logger.info("OCR result: %s | tesseract=%s", data, tess_ok)
        return data

    except Exception as exc:
        logger.error("OCR failed: %s", exc, exc_info=True)
        return IDCardData()


# ── Parsing ───────────────────────────────────────────────────────────────────

_DATE_RE = re.compile(r'\b(\d{1,2}[./-]\d{1,2}[./-]\d{4})\b')

# MRZ line: 30 chars of uppercase letters, digits, and < filler
_MRZ_LINE_RE = re.compile(r'[A-Z0-9<]{25,31}')


def _parse_fields(results: _OcrResult) -> IDCardData:
    by_pos = sorted(
        [(bbox, text.strip(), conf) for bbox, text, conf in results
         if conf > 0.15 and text.strip()],
        key=lambda r: r[0][0][1],
    )
    texts = [t for _, t, _ in by_pos]
    combined = " ".join(texts)

    first_name: Optional[str] = None
    last_name:  Optional[str] = None
    dob:        Optional[str] = None

    # ── 1. Date of birth (numeric, works with English OCR) ───────────────────
    m = _DATE_RE.search(combined)
    if m:
        dob = m.group(1)

    # ── 2. MRZ parsing (works on back of card, always Latin script) ──────────
    mrz_lines = [t for t in texts if _MRZ_LINE_RE.fullmatch(t.replace(" ", ""))]
    if mrz_lines:
        surname, given = _parse_mrz_names(mrz_lines)
        if surname:
            last_name = surname
        if given:
            first_name = given

    # ── 3. Fallback: DOB from MRZ line 2 (YYMMDD) ───────────────────────────
    if not dob and mrz_lines:
        dob = _parse_mrz_dob(mrz_lines)

    logger.debug("MRZ lines found: %d | names: %s / %s | dob: %s",
                 len(mrz_lines), last_name, first_name, dob)

    return IDCardData(
        first_name=first_name,
        last_name=last_name,
        date_of_birth=dob,
    )


# ── MRZ helpers ───────────────────────────────────────────────────────────────

def _parse_mrz_names(mrz_lines: List[str]) -> tuple[Optional[str], Optional[str]]:
    """
    Israeli ID (TD1, 3×30 chars). Names are on line 3:
        SURNAME<<GIVEN_NAME<SECOND_NAME<<<...
    OCR may split MRZ into multiple fragments; we find the name line by
    looking for the '<<' double-filler separator.
    """
    # Normalise: remove spaces, upper-case
    lines = [l.replace(" ", "").upper() for l in mrz_lines]

    # The name line (TD1 line 3) has only letters/single-chevrons before '<<'.
    # Lines 1 & 2 contain digits (doc number, DOB, check digits) before any '<<',
    # so filtering to purely-alpha parts[0] safely skips them.
    name_line = next(
        (l for l in lines
         if "<<" in l and re.match(r'^[A-Z<]+$', l.split("<<", 1)[0])),
        None
    )
    if not name_line:
        return None, None

    # Pad / trim to 30 chars
    name_line = (name_line + "<" * 30)[:30]

    parts = name_line.split("<<", 1)
    surname = _mrz_token(parts[0]) if parts else None
    given   = _mrz_token(parts[1].split("<")[0]) if len(parts) > 1 else None

    return surname, given


def _mrz_token(raw: str) -> Optional[str]:
    """Convert MRZ token (e.g. COHEN) to Title Case, or None if empty."""
    name = raw.replace("<", " ").strip()
    if not name:
        return None
    # Multiple words (e.g. "BEN DAVID") → "Ben David"
    return " ".join(w.title() for w in name.split())


_RTL_CHARS = re.compile(r'[‎‏‪-‮]')  # strip direction marks
_HEB_RE    = re.compile(r'[א-תיִ-פֿ]+')  # Hebrew chars (incl. nikud forms)

# Labels to skip when looking for name values
# Labels to skip when looking for name values.
# NOTE: "ישראל" is deliberately NOT here — it is a common first name. The
# country phrase "מדינת ישראל" sits above the "שם משפחה" anchor, and name
# collection only starts *below* that anchor, so it is excluded by position
# rather than by keyword (which would also drop the real name ישראל).
_SKIP_WORDS = {"מדינת", "זהות", "תעודת", "תאריך", "לידה", "מין",
               "הנפקה", "תוקף", "תשלום", "חתימה", "גיל", "כתובת",
               "israel", "state", "identity", "card", "date", "birth",
               "issued", "expiry", "signature", "sex", "age"}


def _extract_hebrew_names(tess_lines, data: IDCardData) -> None:
    """tess_lines = list of (text, top_y) sorted by Y position."""
    _extract_by_position(tess_lines, data)


_LABEL_WORDS = frozenset({"שם", "משפחה", "פרטי", "לידה", "תאריך", "מין", "שפ",
                           "name", "last", "first", "given", "birth", "date", "sex"})

# Common Hebrew prefixes that Tesseract/font rendering may attach to label roots
_HEB_PREFIX_RE = re.compile(r'^[הוכלמבש]+')


def _strip_heb_prefix(w: str) -> str:
    """
    Strip leading Hebrew prefixes, but ONLY as far as needed to reach a known
    label/skip root.

    The greedy version (``^[הוכלמבש]+``) is wrong for words whose own letters
    are also prefix letters: e.g. משפחה begins with מ and ש, both of which are
    in the prefix set, so "המשפחה" was over-stripped to "פחה" and no longer
    matched the label root "משפחה" — letting the label word leak in as the
    surname. We instead peel one prefix letter at a time and stop the moment
    the remainder is a recognised root.
    """
    if len(w) <= 2:
        return w
    known = _LABEL_WORDS | _SKIP_WORDS
    for k in range(1, len(w) - 1):
        if w[k - 1] not in "הוכלמבש":
            break
        cand = w[k:]
        if cand in known:
            return cand
    return w


def _name_words(text: str) -> list:
    """
    Extract Hebrew name-words from a line, excluding label keywords.
    Handles prefixed forms (ה/ו/כ/ל/מ/ב/ש) so "המשפחה"/"השם"/"הפרטי" are filtered.
    """
    words = _HEB_RE.findall(text)
    result = []
    for w in words:
        bare = _strip_heb_prefix(w)
        if (w in _SKIP_WORDS or w in _LABEL_WORDS
                or bare in _SKIP_WORDS or bare in _LABEL_WORDS):
            continue
        if len(w) >= 2:
            result.append(w)
    return result


def _extract_by_position(lines, data: IDCardData) -> None:
    """
    Position-based name extraction using BOTH labels as boundaries.

    Israeli ID layout (top→bottom):
        שם משפחה  <label>
        <surname value>
        שם פרטי   <label>
        <first-name value>
        תאריך לידה ...

    Surname  = name-words strictly between the "משפחה" label and the "פרטי" label.
    First    = name-words strictly between the "פרטי" label and the date line.

    Reading each value from the region *below its own label* (never from the
    label line itself) is what makes first-name extraction reliable — so we do
    the same for the surname. The label line carries the Arabic sub-label
    (اسم العائلة / الاسم الشخصي), which a heb+eng model turns into Hebrew-looking
    garbage; harvesting the label line would drop that garbage into the surname.
    The label line is used only as a *fallback* when no value line exists below
    it (covers the rare "שם משפחה כהן" single-line layout).
    """
    DATE_WORDS = re.compile(r'תש|בטב|בניס|בסיו|בתמ|בשב|אדר|ניס|אב')

    def _y(pred):
        return next((y for t, y in lines if pred(t)), None)

    last_label_y  = _y(lambda t: "משפחה" in t)
    if last_label_y is None:
        return   # no surname anchor — give up
    first_label_y = _y(lambda t: "פרטי" in t)
    date_y        = _y(lambda t: re.search(r'\d{2}\.\d{2}\.\d{4}', t))

    last_label_text  = next((t for t, y in lines if y == last_label_y), "")
    first_label_text = next((t for t, y in lines if "פרטי" in t), "")

    def _collect(lo_y, hi_y):
        """First name-word cluster with lo_y < y < hi_y (hi_y=None → open)."""
        for text, y in lines:
            if y <= lo_y:
                continue
            if hi_y is not None and y >= hi_y:
                break
            if DATE_WORDS.search(text):
                continue
            words = _name_words(text)
            if words:
                return " ".join(words)
        return None

    # ── Surname: between משפחה label and פרטי label (or date, if no פרטי) ──────
    surname_hi = first_label_y if first_label_y is not None else (
        (date_y + 200) if date_y else None)
    last_name = _collect(last_label_y, surname_hi)
    if not last_name:                       # fallback: value inline on the label
        w = _name_words(last_label_text)
        if w:
            last_name = " ".join(w)

    # ── First name: between פרטי label and the date line ─────────────────────
    first_name = None
    if first_label_y is not None:
        first_hi = (date_y + 200) if date_y else None
        first_name = _collect(first_label_y, first_hi)
        if not first_name:                  # fallback: value inline on the label
            w = _name_words(first_label_text)
            if w:
                first_name = " ".join(w)

    logger.info("Names by position: last=%s first=%s", last_name, first_name)

    if not data.last_name and last_name:
        data.last_name = last_name
    if not data.first_name and first_name and first_name != data.last_name:
        data.first_name = first_name



def _extract_hebrew_names_str(text: str, data: IDCardData) -> None:
    """
    Parse Hebrew names from Tesseract output (heb+eng).

    Strategy:
    1. Look for label lines containing 'משפחה' / 'פרטי' and pick the
       next line with Hebrew words as the value.
    2. Fallback: collect Hebrew-word lines top→bottom, skip known
       non-name words, take first two groups as last_name / first_name.
    """
    clean = _RTL_CHARS.sub("", text)
    lines = [l.strip() for l in clean.splitlines() if l.strip()]

    def _name_from_line(line: str) -> Optional[str]:
        words = _HEB_RE.findall(line)
        words = [w for w in words if w not in _SKIP_WORDS and len(w) >= 2]
        if words:
            return " ".join(words)
        return None

    # ── 1. Label-proximity ────────────────────────────────────────────────────
    for i, line in enumerate(lines):
        if "משפחה" in line and not data.last_name:
            for j in range(i + 1, min(i + 4, len(lines))):
                n = _name_from_line(lines[j])
                if n and "משפחה" not in lines[j] and "פרטי" not in lines[j]:
                    data.last_name = n; break

        if "פרטי" in line and not data.first_name:
            for j in range(i + 1, min(i + 4, len(lines))):
                n = _name_from_line(lines[j])
                if n and "משפחה" not in lines[j] and "פרטי" not in lines[j]:
                    data.first_name = n; break

    # ── 2. Fallback: top-to-bottom Hebrew word clusters ───────────────────────
    if not data.last_name or not data.first_name:
        # Hebrew month/year words that appear in dates — not names
        _DATE_WORDS = re.compile(
            r'תשר|חשו|כסל|טבת|שבט|אדר|ניס|איי|סיו|תמו|אב|אלו|'
            r'תש|תקו|בתמ|ביר|בטב|בשב|בניס|בסיו|בתמו|בנו'
        )
        label_re = re.compile(r'משפחה|פרטי|לידה|תאריך|מין|sex|birth|name|date|תוקף|בתוקף')
        hebrew_lines = [
            _name_from_line(l) for l in lines
            if _HEB_RE.search(l)
            and not label_re.search(l.lower())
            and not _DATE_WORDS.search(l)
            and not re.search(r'\d{2}\.\d{2}\.\d{4}', l)
        ]
        hebrew_lines = [h for h in hebrew_lines if h]
        # Assign only to fields that are still empty, and never duplicate
        for candidate in hebrew_lines:
            if not data.last_name and candidate != data.first_name:
                data.last_name = candidate
            elif not data.first_name and candidate != data.last_name:
                data.first_name = candidate
            if data.last_name and data.first_name:
                break


def _parse_mrz_dob(mrz_lines: List[str]) -> Optional[str]:
    """
    DOB in MRZ TD1 line 2 starts at position 0: YYMMDD + check digit.
    Converts to DD.MM.YYYY (assuming 21st century if YY <= current year).
    """
    import datetime
    lines = [l.replace(" ", "").upper() for l in mrz_lines]
    # Line 2: starts with DOB YYMMDD at position 0, OR at various offsets
    dob_re = re.compile(r'(\d{6})\d')  # 6 digits followed by check digit
    current_year_2d = datetime.date.today().year % 100
    for line in lines:
        m = dob_re.search(line)
        if m:
            raw = m.group(1)   # YYMMDD
            yy, mm, dd = raw[:2], raw[2:4], raw[4:6]
            if not (1 <= int(mm) <= 12 and 1 <= int(dd) <= 31):
                continue
            century = "19" if int(yy) > current_year_2d else "20"
            return f"{dd}.{mm}.{century}{yy}"
    return None
