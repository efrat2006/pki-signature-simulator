package com.authentisign.desktop.security.KeyStore;

import java.security.PrivateKey;
import java.security.cert.Certificate;

public record KeyAndChain(PrivateKey privateKey, Certificate[] chain) {
}
