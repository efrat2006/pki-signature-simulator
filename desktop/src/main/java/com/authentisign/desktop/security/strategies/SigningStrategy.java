package com.authentisign.desktop.security.strategies;

import java.security.*;
import java.security.cert.Certificate;

public interface SigningStrategy {

    KeyPair generateKeys() throws Exception;
    byte[] sign(byte[] data, PrivateKey privateKey, Certificate[] chain) throws Exception;
    boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception;
    String getAlgorithmName();
    String getSignatureAlgorithm();
}