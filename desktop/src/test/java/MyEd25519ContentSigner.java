import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.PrivateKey;
import com.authentisign.desktop.security.strategies.Ed25519;

public class MyEd25519ContentSigner implements ContentSigner {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final byte[] seed;

    public MyEd25519ContentSigner(PrivateKey privateKey) throws Exception {
        Ed25519PrivateKeyParameters p =
                (Ed25519PrivateKeyParameters) PrivateKeyFactory.createKey(privateKey.getEncoded());
        this.seed = p.getEncoded();   // חילוץ ה-seed בן 32 הבתים
    }

    @Override public OutputStream getOutputStream() { return buffer; }

    @Override public AlgorithmIdentifier getAlgorithmIdentifier() {
        return new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519);
    }

    @Override public byte[] getSignature() {
        System.out.println(">>> MyEd25519ContentSigner: חותם עם המימוש שלי! <<<");
        return Ed25519.sign(seed, buffer.toByteArray());
    }
}