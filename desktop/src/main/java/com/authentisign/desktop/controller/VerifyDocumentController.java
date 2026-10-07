package com.authentisign.desktop.controller;

import com.authentisign.desktop.services.signing.ocsp.OcspService;
import com.authentisign.desktop.services.signing.pdf.PdfSignatureVerifier;
import com.authentisign.desktop.services.signing.pdf.PdfSignatureVerifier.VerificationOutcome;
import javafx.application.Platform;
import java.io.File;
import java.util.function.Consumer;


public class VerifyDocumentController {

    private final PdfSignatureVerifier verifier;

    public VerifyDocumentController() {
        this.verifier = new PdfSignatureVerifier(new OcspService());
    }

    //אימות החתימה
    public void verify(File signedPdf, Consumer<VerificationOutcome> onResult) {
        new Thread(() -> {
            VerificationOutcome outcome = verifier.verify(signedPdf);
            Platform.runLater(() -> onResult.accept(outcome));
        }, "verify-pdf").start();
    }
}