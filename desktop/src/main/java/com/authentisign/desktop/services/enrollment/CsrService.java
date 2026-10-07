package com.authentisign.desktop.services.enrollment;

import com.authentisign.desktop.security.CsrGenerator;
import com.authentisign.desktop.security.strategies.SigningStrategy;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

public class CsrService {

    private final CsrGenerator csrGen = new CsrGenerator();


    public String generateCsr(KeyStore ks, char[] password, String alias, String commonName,
                              SigningStrategy strategy) throws Exception {
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
        Certificate cert = ks.getCertificate(alias);

        if (privateKey == null || cert == null) {
            throw new Exception("Alias '" + alias + "' not found or contains no data.");
        }

        PublicKey publicKey = cert.getPublicKey();
        return csrGen.generateCSRPem(publicKey, privateKey, commonName, strategy.getSignatureAlgorithm());
    }
}
