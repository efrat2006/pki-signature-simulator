import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import com.authentisign.desktop.security.strategies.Ed25519;
public class MyEd25519Test2 {

    // ── חמישה וקטורים אמיתיים שאומתו מול OpenSSL ─────────────────────
    // {seed, message, expectedPublicKey, expectedSignature}
    static final String[][] VECTORS = {
            {"9d61b19deffebe72347bcf3e35f4d34c8fddad0d7e00e93e3f2c1cd93b4e18ab", "",
                    "9ef7a8b9c9b4e6cb1f4c211c0e5388b27ab0acc345416b84fa7e989a26308186",
                    "6ccc29caf3e3443a673555752346722de2d5f0318e6d0c4a112d3ac7bbdfee909c348b924e8441f38f8c2ce24752cd96cfe29174a16a7526d63b3ce7d79e1301"},
            {"4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb", "72",
                    "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
                    "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"},
            {"c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7", "af82",
                    "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
                    "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"},
            {"0000000000000000000000000000000000000000000000000000000000000000", "68656c6c6f20776f726c64",
                    "3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29",
                    "b0b47780f096ae60bfff8d8e7b19c36b321ae6e69cca972f2ff987ef30f20d29774b53bae404485c4391ddf1b3f37aaa8a9747f984eb0884e8aa533386e73305"},
            {"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                    "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
                    "76a1592044a6e4f511265bca73a604d90b0529d1df602be30a19a9257660d1f5",
                    "2d43aea46ff253c37f652792fec4986f9d1e9f624e93a4bdf1e5a474949eeda4a77273a6224304aedbba115549c0c9d521ee397a707dca16754843f53969c404"},
    };

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        int passed = 0, failed = 0;

        // ═══ חלק א׳: וקטורים ידועים (Known-Answer Tests) ═══
        System.out.println("── חלק א׳: וקטורים ידועים מול OpenSSL ──");
        for (int i = 0; i < VECTORS.length; i++) {
            byte[] seed = hex(VECTORS[i][0]);
            byte[] msg  = hex(VECTORS[i][1]);
            byte[] expPub = hex(VECTORS[i][2]);
            byte[] expSig = hex(VECTORS[i][3]);

            byte[] gotPub = Ed25519.publicKey(seed);
            byte[] gotSig = Ed25519.sign(seed, msg);

            boolean pubOk = Arrays.equals(gotPub, expPub);
            boolean sigOk = Arrays.equals(gotSig, expSig);
            boolean vrfOk = Ed25519.verify(expPub, msg, expSig);   // גם מאמת חתימה תקנית

            if (pubOk && sigOk && vrfOk) { passed++; System.out.println("✓ וקטור " + (i+1) + " תואם במלואו (pub+sig+verify)"); }
            else { failed++; System.out.println("✗ וקטור " + (i+1) + " נכשל (pub=" + pubOk + " sig=" + sigOk + " verify=" + vrfOk + ")"); }
        }

        // ═══ חלק ב׳: אינטרופרביליות מול BouncyCastle ═══
        // זו הבדיקה הקריטית: BC הוא מי שיאמת בפועל את החתימות ב-CMS/PDF.
        System.out.println("\n── חלק ב׳: תאימות דו-כיוונית מול BouncyCastle ──");
        SecureRandom rnd = new SecureRandom();
        int rounds = 200;
        int mine2bc = 0, bc2mine = 0;

        for (int i = 0; i < rounds; i++) {
            byte[] seed = new byte[32]; rnd.nextBytes(seed);
            byte[] msg  = new byte[rnd.nextInt(100)]; rnd.nextBytes(msg);

            // (1) חותמים עם הקוד שלך → מאמתים עם BC
            byte[] myPub = Ed25519.publicKey(seed);
            byte[] mySig = Ed25519.sign(seed, msg);
            if (bcVerify(myPub, msg, mySig)) mine2bc++;

            // (2) חותמים עם BC → מאמתים עם הקוד שלך
            byte[] bcSig = bcSign(seed, msg);
            if (Ed25519.verify(myPub, msg, bcSig)) bc2mine++;
        }

        if (mine2bc == rounds) { passed++; System.out.println("✓ BC אימת את כל " + rounds + " החתימות שלך"); }
        else { failed++; System.out.println("✗ BC דחה " + (rounds - mine2bc) + " מחתימותיך!"); }

        if (bc2mine == rounds) { passed++; System.out.println("✓ הקוד שלך אימת את כל " + rounds + " חתימות ה-BC"); }
        else { failed++; System.out.println("✗ הקוד שלך דחה " + (rounds - bc2mine) + " חתימות תקניות!"); }

        // ═══ סיכום ═══
        System.out.println("\n════════════════════════════");
        System.out.println("עברו: " + passed + " | נכשלו: " + failed);
        System.out.println(failed == 0
                ? "✅ המימוש שלך תקני ותואם מלא ל-OpenSSL ו-BouncyCastle!"
                : "⚠️ יש כשלים - ראי פירוט למעלה");
    }

    // ── עוזרי BouncyCastle ──────────────────────────────────────────
    static byte[] bcSign(byte[] seed, byte[] msg) {
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, new Ed25519PrivateKeyParameters(seed, 0));
        s.update(msg, 0, msg.length);
        return s.generateSignature();
    }
    static boolean bcVerify(byte[] pub, byte[] msg, byte[] sig) {
        Ed25519Signer v = new Ed25519Signer();
        v.init(false, new Ed25519PublicKeyParameters(pub, 0));
        v.update(msg, 0, msg.length);
        return v.verifySignature(sig);
    }

    // ── עוזרי hex ──────────────────────────────────────────────────
    static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}