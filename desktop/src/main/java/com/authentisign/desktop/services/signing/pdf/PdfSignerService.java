package com.authentisign.desktop.services.signing.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.Loader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

public class PdfSignerService {

    public void signPdf(File inputPdf, File outputPdf, PdfSignatureHandler sigHandler) throws Exception {
        try (PDDocument doc = Loader.loadPDF(inputPdf);
             FileOutputStream fos = new FileOutputStream(outputPdf)) {

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("AuthentiSign user");
            signature.setReason("Document signing");
            signature.setSignDate(Calendar.getInstance());

            doc.addSignature(signature, sigHandler);

            ExternalSigningSupport externalSigning =
                    doc.saveIncrementalForExternalSigning(fos);

            InputStream contentStream = externalSigning.getContent();
            byte[] dataToSign = contentStream.readAllBytes();


            byte[] cmsSignature = sigHandler.sign(
                    new ByteArrayInputStream(dataToSign)
            );

            //הכנס את החתימה בחזרה
            externalSigning.setSignature(cmsSignature);
        }
    }
}
