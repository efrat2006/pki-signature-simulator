# הוראות הרצה מקומית – AuthentiSign

תוכנת דסקטופ לחתימה דיגיטלית מאושרת על מסמכי PDF, יחד עם שרת המדמה רשות מאשרת (CA) להנפקת תעודות דיגיטליות.

## מבנה הפרויקט

| תיקייה | תיאור | טכנולוגיות |
|---|---|---|
| `ca-server/` | שרת ה-CA: הנפקת תעודות מ-CSR, ביטול תעודות, OCSP, אימות זהות (OTP, שאלות אבטחה, זיהוי פנים דרך WebSocket) | Java 21, Spring Boot 3, PostgreSQL, BouncyCastle |
| `desktop/` | אפליקציית הדסקטופ: הרשמה והתחברות, יצירת מפתחות ו-CSR, חתימה ואימות של PDF | Java, JavaFX, PDFBox, OpenCV, SQL Server |
| `api-gateway/` | הגדרות Nginx שמנתבות בקשות לשרת ה-CA ולשרת זיהוי הפנים | Nginx |
| `identity-verification/` | שירות זיהוי הפנים (יתווסף בהמשך) | Python, FastAPI

## הרצה מקומית

### 1. שרת ה-CA
מגדירים משתני סביבה. לכולם יש ערכי ברירת מחדל לפיתוח, חוץ מה-Gmail:

```
DB_PASSWORD=...            # סיסמת PostgreSQL
GMAIL_USERNAME=...         # כתובת Gmail לשליחת OTP
GMAIL_APP_PASSWORD=...     # App Password של גוגל, לא הסיסמה הרגילה
```

יוצרים keystore ל-HTTPS ב-`ca-server/src/main/resources/keystore.p12`:
```
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 365 -storepass 123456 -dname "CN=localhost"
```
אם אין keystore של ה-CA (`root-ca.p12`), השרת יוצר אותו אוטומטית בעלייה הראשונה.

```
cd ca-server
./mvnw spring-boot:run
```

### 2. ה-API Gateway
מורידים את [Nginx לווינדוס](https://nginx.org/en/download.html), מעתיקים את `conf/nginx.conf` לתיקיית `conf` שלו, ויוצרים תעודה מקומית:
```
openssl req -x509 -nodes -newkey rsa:2048 -days 365 -keyout conf/localhost.key -out conf/localhost.crt -config san.cnf
```
ה-gateway מאזין על פורט 8080.

### 3. אפליקציית הדסקטופ
צריך SQL Server מקומי עם מסד הנתונים `SigningAppDB`. פרטי החיבור נמצאים ב-`src/main/resources/META-INF/persistence.xml`.
```
cd desktop
mvn javafx:run
```

### 4. שירות זיהוי הפנים (identity-verification)
דורש Python ומערכת Windows (הסקריפט `install.ps1`).
```
cd identity-verification
python -m venv venv
.\install.ps1
.\venv\Scripts\pip.exe install pytesseract
python generate_cert.py      # יוצר cert.pem + key.pem מקומיים
python main.py               # https://localhost:8443
```
- מודלי insightface ו-EasyOCR יורדים אוטומטית בהרצה הראשונה (כ-400MB).
- לזיהוי שמות בעברית: מתקינים [Tesseract-OCR](https://github.com/UB-Mannheim/tesseract/wiki) ב-`C:\Program Files\Tesseract-OCR`,
  ומורידים את [heb.traineddata](https://github.com/tesseract-ocr/tessdata/raw/main/heb.traineddata) לתיקייה `identity-verification/tessdata/`.
- דף בדיקה בדפדפן: https://localhost:8443/
