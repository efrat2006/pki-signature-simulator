package com.pki.ca.strategies;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

public interface SigningStrategy {

    KeyPair generateKeys() throws Exception;
    byte[] sign(byte[] data, PrivateKey privateKey, Certificate[] chain) throws Exception;
    boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception;
    String getAlgorithmName();
    String getSignatureAlgorithm();
}