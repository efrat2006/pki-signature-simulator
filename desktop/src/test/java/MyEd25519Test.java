import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.authentisign.desktop.security.strategies.Ed25519;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;

public class MyEd25519Test {

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());

        int passed = 0, failed = 0;

        // ── מבחן 1: חתימה ואימות בסיסיים ─────────────────────────────
        {
            byte[] seed = Ed25519.generateSeed();
            byte[] pub  = Ed25519.publicKey(seed);
            byte[] msg  = "hello ed25519".getBytes(StandardCharsets.UTF_8);

            byte[] sig  = Ed25519.sign(seed, msg);
            boolean ok  = Ed25519.verify(pub, msg, sig);

            if (ok && sig.length == 64 && pub.length == 32) { passed++; System.out.println("✓ 1. sign+verify בסיסי עובד"); }
            else { failed++; System.out.println("✗ 1. sign+verify נכשל (ok=" + ok + ", sigLen=" + sig.length + ")"); }
        }

        // ── מבחן 2: חתימה על הודעה ריקה ──────────────────────────────
        {
            byte[] seed = Ed25519.generateSeed();
            byte[] pub  = Ed25519.publicKey(seed);
            byte[] msg  = new byte[0];

            byte[] sig  = Ed25519.sign(seed, msg);
            if (Ed25519.verify(pub, msg, sig)) { passed++; System.out.println("✓ 2. הודעה ריקה עובדת"); }
            else { failed++; System.out.println("✗ 2. הודעה ריקה נכשלה"); }
        }

        // ── מבחן 3: הודעה משתנה → החתימה נדחית ───────────────────────
        {
            byte[] seed = Ed25519.generateSeed();
            byte[] pub  = Ed25519.publicKey(seed);
            byte[] msg  = "original".getBytes(StandardCharsets.UTF_8);
            byte[] sig  = Ed25519.sign(seed, msg);

            byte[] tampered = "0riginal".getBytes(StandardCharsets.UTF_8);   // שינוי תו אחד
            if (!Ed25519.verify(pub, tampered, sig)) { passed++; System.out.println("✓ 3. הודעה שהשתנתה נדחית כראוי"); }
            else { failed++; System.out.println("✗ 3. הודעה שהשתנתה עברה אימות - באג!"); }
        }

        // ── מבחן 4: חתימה משתנה → נדחית ──────────────────────────────
        {
            byte[] seed = Ed25519.generateSeed();
            byte[] pub  = Ed25519.publicKey(seed);
            byte[] msg  = "data".getBytes(StandardCharsets.UTF_8);
            byte[] sig  = Ed25519.sign(seed, msg);

            byte[] badSig = sig.clone();
            badSig[0] ^= 0x01;   // הופכים ביט אחד
            if (!Ed25519.verify(pub, msg, badSig)) { passed++; System.out.println("✓ 4. חתימה שהשתנתה נדחית כראוי"); }
            else { failed++; System.out.println("✗ 4. חתימה שהשתנתה עברה אימות - באג!"); }
        }

        // ── מבחן 5: מפתח ציבורי אחר → נדחה ───────────────────────────
        {
            byte[] seedA = Ed25519.generateSeed();
            byte[] seedB = Ed25519.generateSeed();
            byte[] pubB  = Ed25519.publicKey(seedB);
            byte[] msg   = "signed by A".getBytes(StandardCharsets.UTF_8);
            byte[] sigA  = Ed25519.sign(seedA, msg);

            if (!Ed25519.verify(pubB, msg, sigA)) { passed++; System.out.println("✓ 5. אימות עם מפתח זר נדחה כראוי"); }
            else { failed++; System.out.println("✗ 5. מפתח זר עבר אימות - באג!"); }
        }

        // ── מבחן 6: דטרמיניזם — אותו קלט מייצר אותה חתימה ────────────
        {
            byte[] seed = Ed25519.generateSeed();
            byte[] msg  = "deterministic".getBytes(StandardCharsets.UTF_8);
            byte[] sig1 = Ed25519.sign(seed, msg);
            byte[] sig2 = Ed25519.sign(seed, msg);

            if (Arrays.equals(sig1, sig2)) { passed++; System.out.println("✓ 6. החתימה דטרמיניסטית (כנדרש ב-Ed25519)"); }
            else { failed++; System.out.println("✗ 6. החתימה לא דטרמיניסטית - באג!"); }
        }

        // ── מבחן 7: וקטור בדיקה רשמי מ-RFC 8032 ──────────────────────
        // המבחן החשוב ביותר: השוואה מול ערכים ידועים מהתקן עצמו.
        {
            byte[] seed = hex("9d61b19deffebe72347bcf3e35f4d34c8fddad0d7e00e93e3f2c1cd93b4e18ab");
            byte[] msg  = new byte[0];
            byte[] expectedPub = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
            byte[] expectedSig = hex(
                    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                            "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");

            byte[] pub = Ed25519.publicKey(seed);
            byte[] sig = Ed25519.sign(seed, msg);

            boolean pubOk = Arrays.equals(pub, expectedPub);
            boolean sigOk = Arrays.equals(sig, expectedSig);

            if (pubOk && sigOk) { passed++; System.out.println("✓ 7. וקטור RFC 8032 תואם בדיוק לתקן"); }
            else {
                failed++;
                System.out.println("✗ 7. וקטור RFC 8032 לא תואם! (pubOk=" + pubOk + ", sigOk=" + sigOk + ")");
                System.out.println("     pub שלך:   " + hexStr(pub));
                System.out.println("     pub צפוי:  " + hexStr(expectedPub));
                System.out.println("     sig שלך:   " + hexStr(sig));
                System.out.println("     sig צפוי:  " + hexStr(expectedSig));
            }
        }

        // ── סיכום ────────────────────────────────────────────────────
        System.out.println("\n════════════════════════════");
        System.out.println("עברו: " + passed + " | נכשלו: " + failed);
        System.out.println(failed == 0 ? "✅ המימוש שלך תקין ותואם לתקן!" : "⚠️ יש מבחנים שנכשלו - ראי פירוט למעלה");
    }

    // עזר: המרת מחרוזת hex למערך בתים
    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
    private static String hexStr(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}