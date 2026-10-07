package com.authentisign.desktop;



import com.authentisign.desktop.ui.DigSignApp;

import javafx.application.Application;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.Security;

public class Main {
    public static void main(String[] args) {

        Application.launch(DigSignApp.class, args);

        if (Security.getProvider("BC") == null) {

            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
