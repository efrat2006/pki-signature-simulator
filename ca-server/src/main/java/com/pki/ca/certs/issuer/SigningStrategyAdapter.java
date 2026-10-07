package com.pki.ca.certs.issuer;


import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.RuntimeOperatorException;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import com.pki.ca.strategies.SigningStrategy;

//מחלקה המחברת בין הBC לED25519
public class SigningStrategyAdapter implements ContentSigner {
    private final SigningStrategy strategy;
    private final PrivateKey caPrivateKey;
    private final X509Certificate caCert;
    private final DataBuffer dataBuffer;

    public SigningStrategyAdapter(SigningStrategy strategy, PrivateKey caPrivateKey, X509Certificate caCert) {
        this.strategy = strategy;
        this.caPrivateKey = caPrivateKey;
        this.caCert = caCert;
        this.dataBuffer = new DataBuffer();
    }

    //חתימה ה-CA על התעודה
    @Override
    public byte[] getSignature() {
        try {
            byte[] data = dataBuffer.toByteArray();
            if (data.length == 0) {
                throw new RuntimeOperatorException("No data to sign, buffer is empty");
            }

            //בונה שרשרת תעודות
            java.security.cert.Certificate[] chain = (caCert != null)
                    ? new java.security.cert.Certificate[] { caCert }
                    : null;
            byte[] signature = strategy.sign(data, caPrivateKey, chain);
            return signature;
        } catch (Exception e) {
            throw new RuntimeOperatorException("Failed to sign data: ", e);
        }
    }

    //כותב לתוך הבאפר
    @Override
    public OutputStream getOutputStream() {
        return dataBuffer.getOutputStream();
    }

    //זהות האלגוריתם
    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return new AlgorithmIdentifier(
                new ASN1ObjectIdentifier("1.3.101.112"),
                null
        );
    }
}