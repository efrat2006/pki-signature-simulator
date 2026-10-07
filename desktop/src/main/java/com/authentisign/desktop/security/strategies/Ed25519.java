package com.authentisign.desktop.security.strategies;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;


public class Ed25519 implements SigningStrategy {

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public KeyPair generateKeys() throws Exception {
        byte[] seed = generateSeed();
        Ed25519PrivateKeyParameters sk = new Ed25519PrivateKeyParameters(seed, 0);
        Ed25519PublicKeyParameters pk = sk.generatePublicKey();

        PrivateKeyInfo pki = PrivateKeyInfoFactory.createPrivateKeyInfo(sk);
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pk);

        KeyFactory kf = KeyFactory.getInstance("Ed25519", "BC");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pki.getEncoded()));
        PublicKey  pub  = kf.generatePublic(new X509EncodedKeySpec(spki.getEncoded()));
        return new KeyPair(pub, priv);
    }

    @Override
    public byte[] sign(byte[] data, PrivateKey privateKey, Certificate[] chain) throws Exception {
        byte[] seed = extractSeed(privateKey);
        return sign(seed, data);
    }

    @Override
    public boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        byte[] raw = extractRawPublicKey(publicKey);
        return verify(raw, data, signature);
    }

    @Override public String getAlgorithmName()     {
        return "Ed25519";
    }

    @Override public String getSignatureAlgorithm() {
        return "Ed25519";
    }


    private static byte[] extractSeed(PrivateKey privateKey) throws Exception {
        Ed25519PrivateKeyParameters p =
                (Ed25519PrivateKeyParameters) PrivateKeyFactory.createKey(privateKey.getEncoded());
        return p.getEncoded();
    }

    private static byte[] extractRawPublicKey(PublicKey publicKey) throws Exception {
        Ed25519PublicKeyParameters p =
                (Ed25519PublicKeyParameters) PublicKeyFactory.createKey(publicKey.getEncoded());
        return p.getEncoded();
    }


    private static final BigInteger TWO  = BigInteger.valueOf(2);
    private static final BigInteger ONE  = BigInteger.ONE;
    private static final BigInteger ZERO = BigInteger.ZERO;
    //מגדיר את השדה בו נמצאים X, Y
    private static final BigInteger P = TWO.pow(255).subtract(BigInteger.valueOf(19));
    //מגדיר כמה ססקלרים שונים קיימים
    private static final BigInteger L = TWO.pow(252).add(new BigInteger("27742317777372353535851937790883648493"));
    //קובע את צורת העקומה המדויקת
    private static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
    private static final BigInteger SQRT_M1 = TWO.modPow(P.subtract(ONE).divide(BigInteger.valueOf(4)), P);
    //נקודת ההתחלה
    private static final Point B = new Point(
            new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"),
            new BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960"));

    static final class Point {
        final BigInteger X, Y, Z, T;
        Point(BigInteger X, BigInteger Y, BigInteger Z, BigInteger T) { this.X = X; this.Y = Y; this.Z = Z; this.T = T; }
        Point(BigInteger x, BigInteger y) { this(x.mod(P), y.mod(P), ONE, x.multiply(y).mod(P)); }
        static final Point IDENTITY = new Point(ZERO, ONE, ONE, ZERO);

        // חיבור 2 נקודות שונות
        Point add(Point o) {
            BigInteger a = Y.subtract(X).multiply(o.Y.subtract(o.X)).mod(P);
            BigInteger b = Y.add(X).multiply(o.Y.add(o.X)).mod(P);
            BigInteger c = T.multiply(TWO).multiply(D).multiply(o.T).mod(P);
            BigInteger d = Z.multiply(TWO).multiply(o.Z).mod(P);
            BigInteger e = b.subtract(a), f = d.subtract(c), g = d.add(c), h = b.add(a);
            return new Point(
                    e.multiply(f).mod(P),
                    g.multiply(h).mod(P),
                    f.multiply(g).mod(P),
                    e.multiply(h).mod(P));
        }

        //הכפלת נקודה בעצמה (חיבור נקודה לעצמה)
        Point dbl() {
            return add(this);
        }

        //הכפלת נקודה בסקלר
        Point scalarMul(BigInteger n) {
            Point r = IDENTITY;
            for (int i = 255; i >= 0; i--) {
                //הכפל את B בעצמה
                r = r.dbl();
                //אם הביט 1, הוסף את B
                if (n.testBit(i))
                    r = r.add(this);
            }
            return r;
        }

        BigInteger[] affine() {
            BigInteger zi = Z.modInverse(P);
            return new BigInteger[]{ X.multiply(zi).mod(P), Y.multiply(zi).mod(P) };
        }
        boolean eq(Point o) {
            BigInteger[] a = affine(), b = o.affine();
            return a[0].equals(b[0]) && a[1].equals(b[1]);
        }
    }

    private static byte[] encodePoint(Point p) {
        BigInteger[] a = p.affine();
        byte[] s = leBytes(a[1], 32);
        if (a[0].testBit(0)) s[31] |= (byte) 0x80;
        return s;
    }

    private static Point decodePoint(byte[] s) {
        if (s.length != 32) throw new IllegalArgumentException("bad length");
        byte[] yb = s.clone();
        int sign = (yb[31] & 0x80) != 0 ? 1 : 0;
        yb[31] &= 0x7F;
        BigInteger y = leToBig(yb);
        if (y.compareTo(P) >= 0) throw new IllegalArgumentException("y >= p");
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger u  = y2.subtract(ONE).mod(P);
        BigInteger v  = D.multiply(y2).add(ONE).mod(P);
        BigInteger v3 = v.multiply(v).mod(P).multiply(v).mod(P);
        BigInteger v7 = v3.multiply(v3).mod(P).multiply(v).mod(P);
        BigInteger pe = P.subtract(BigInteger.valueOf(5)).divide(BigInteger.valueOf(8));
        BigInteger x  = u.multiply(v3).mod(P).multiply(u.multiply(v7).mod(P).modPow(pe, P)).mod(P);
        BigInteger check = v.multiply(x).mod(P).multiply(x).mod(P);
        if (!check.equals(u)) {
            if (check.equals(u.negate().mod(P))) x = x.multiply(SQRT_M1).mod(P);
            else throw new IllegalArgumentException("no square root");
        }
        if (x.signum() == 0 && sign == 1) throw new IllegalArgumentException("x = 0 with sign");
        if ((x.testBit(0) ? 1 : 0) != sign) x = P.subtract(x);
        return new Point(x, y);
    }

    //הפיכת סדר הבתים כשהקלט הוא מהנמוך לגבוה
    private static BigInteger leToBig(byte[] in) {
        byte[] r = new byte[in.length];
        for (int i = 0; i < in.length; i++)
            r[i] = in[in.length - 1 - i];
        return new BigInteger(1, r);
    }

   //הפיכת סדר הבתים כשהקלט הוא מגבוה לנמוך
    private static byte[] leBytes(BigInteger n, int len) {
        byte[] out = new byte[len]; byte[] b = n.toByteArray();
        for (int i = b.length - 1, j = 0; i >= 0 && j < len; i--, j++) out[j] = b[i];
        return out;
    }

    //שליחה לפונקציית גיבוב
    private static byte[] sha512(byte[] m) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(m); }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //משרשר מערכים
    private static byte[] concat(byte[]... arrs) {
        int len = 0; for (byte[] a : arrs) len += a.length;
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] a : arrs) {
            System.arraycopy(a, 0, out, o, a.length);
            o += a.length; }
        return out;
    }

    //איפוס סיביות
    private static BigInteger clampScalar(byte[] h32) {
        byte[] a = Arrays.copyOf(h32, 32);
        a[0] &= (byte) 0xF8;
        a[31] &= (byte) 0x7F;
        a[31] |= (byte) 0x40;
        return leToBig(a);
    }

    //יצירת seed
    public static byte[] generateSeed() {
        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        return seed;
    }

    //גזירת המפתח הציבורי מה-seed
    public static byte[] publicKey(byte[] seed) {
        byte[] h = sha512(seed);
        return encodePoint(B.scalarMul(clampScalar(Arrays.copyOfRange(h, 0, 32))));
    }
    //חתימה
    public static byte[] sign(byte[] seed, byte[] msg) {
        byte[] h = sha512(seed);
        BigInteger a   = clampScalar(Arrays.copyOfRange(h, 0, 32));
        byte[] prefix  = Arrays.copyOfRange(h, 32, 64);
        byte[] A       = encodePoint(B.scalarMul(a));
        BigInteger r = leToBig(sha512(concat(prefix, msg))).mod(L);
        byte[] R     = encodePoint(B.scalarMul(r));
        BigInteger k = leToBig(sha512(concat(R, A, msg))).mod(L);
        BigInteger S = r.add(k.multiply(a)).mod(L);
        return concat(R, leBytes(S, 32));
    }

    //אימות החתימה
    public static boolean verify(byte[] publicKey, byte[] msg, byte[] sig) {
        if (sig.length != 64 || publicKey.length != 32) return false;
        try {
            byte[] R  = Arrays.copyOfRange(sig, 0, 32);
            byte[] Sb = Arrays.copyOfRange(sig, 32, 64);
            BigInteger S = leToBig(Sb);
            if (S.compareTo(L) >= 0) return false;
            Point Rp = decodePoint(R);
            Point A  = decodePoint(publicKey);
            BigInteger k = leToBig(sha512(concat(R, publicKey, msg))).mod(L);
            return B.scalarMul(S).eq(Rp.add(A.scalarMul(k)));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void debugPublicKeyDerivation() {
        byte[] seed = hex("9d61b19deffebe72347bcf3e35f4d34c8fddad0d7e00e93e3f2c1cd93b4e18ab");
        byte[] h = sha512(seed);
        System.out.println("h (64B)     : " + hexStr(h));
        BigInteger a = clampScalar(Arrays.copyOfRange(h, 0, 32));
        System.out.println("a (scalar)  : " + a.toString(16));
        byte[] A = encodePoint(B.scalarMul(a));
        System.out.println("A encoded   : " + hexStr(A));
        System.out.println("A expected  : d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
    }
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