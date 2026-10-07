package com.authentisign.desktop.controller;

import com.authentisign.desktop.database.entities.User;
import com.authentisign.desktop.security.KeyStore.KeyAndChain;
import com.authentisign.desktop.security.KeyStore.KeyStoreService;
import com.authentisign.desktop.security.strategies.Ed25519;
import com.authentisign.desktop.security.strategies.SigningStrategy;
import com.authentisign.desktop.services.signing.ocsp.OcspService;
import com.authentisign.desktop.services.signing.pdf.PdfSignatureHandler;
import com.authentisign.desktop.services.signing.pdf.PdfSignerService;
import javafx.application.Platform;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.File;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class SignDocumentController {

    private final User currentUser;
    private final KeyStoreService keyStoreService;
    private final PdfSignerService pdfSignerService;

    private final Map<String, char[]> unlockedPasswords = new HashMap<>();
    private final Map<String, X509Certificate> unlockedCerts = new HashMap<>();

    private File selectedFile;
    private String selectedAlias;

    public SignDocumentController(User currentUser) {
        this.currentUser = currentUser;
        this.keyStoreService = new KeyStoreService();
        this.pdfSignerService = new PdfSignerService();
        ensureBcProvider();
    }


    //מעדכנת שנבחר קובץ
    public void setSelectedFile(File file) {
        this.selectedFile = file;
    }

    //מביאה את הקובץ שנבחר
    public File getSelectedFile() {
        return selectedFile;
    }

    //מעדכנת שנבחרה תעודה
    public void setSelectedAlias(String alias) {
        this.selectedAlias = alias;
    }

    public String getSelectedAlias() {
        return selectedAlias;
    }

    //בודק האם אפשר כבר לחתום על התעודה
    public boolean isReadyToSign() {
        return selectedFile != null && selectedAlias != null;
    }

    //מחזירה את רשימת התעודות של המשתמש
    public List<String> listAvailableAliases() {
        String userDir = keyStoreService.getUserDirectory(currentUser.getEmail());
        File dir = new File(userDir);
        //עתיק ולכן מחזיר מערך ולא רשימה
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".p12"));

        if (files == null || files.length == 0) {
            return List.of();
        }

        //המרה ממערך לרשימה, חילוץ השם התעודה ומיון בסדר אלפבתי מחזיר רשימה
        return Arrays.stream(files)
                .map(File::getName)
                .map(name -> name.substring(0, name.length() - ".p12".length()))
                .sorted()
                .collect(Collectors.toList());
    }

    //פתיחת תעודה
    public void unlockCertificate(String alias, String password, Consumer<UnlockResult> onResult) {
        if (password == null || password.isEmpty()) {
            onResult.accept(new UnlockResult(false, null, "יש להזין את הסיסמה של התעודה הזו"));
            return;
        }

        String filePath = keyStoreService.getPathForAlias(currentUser.getEmail(), alias);
        char[] pwChars = password.toCharArray();

        new Thread(() -> {
            X509Certificate cert = null;
            Exception failure = null;
            try {
                //שולחים את העתק של הסיסמה כי הפונקציה תדרוס את המערך ועדין צריך אותו
                cert = keyStoreService.getCertificateDetails(filePath, pwChars.clone(), alias);
                cert.checkValidity();

            } catch (Exception ex) {
                failure = ex;
            }

            X509Certificate finalCert = cert;
            Exception finalFailure = failure;

            Platform.runLater(() -> {
                if (finalFailure != null || finalCert == null) {
                    String msg = (finalFailure != null && finalFailure.getMessage() != null
                            && finalFailure.getMessage().toLowerCase().contains("expired"))
                            ? "תוקף התעודה הזו פג"
                            : "סיסמה שגויה, או שגיאה בפתיחת התעודה";
                    onResult.accept(new UnlockResult(false, null, msg));
                    return;
                }

                //כשהפתיחה הצליחה שומרים את התעודה והסיסמה
                unlockedPasswords.put(alias, password.toCharArray());
                unlockedCerts.put(alias, finalCert);
                onResult.accept(new UnlockResult(true, finalCert, null));
            });
        }, "unlock-cert-" + alias).start();
    }

   //חתימה על ה-PDF הנבחר
    public void sign(File outputFile, Consumer<SignResult> onResult) {
        File inputFile = this.selectedFile;
        String alias = this.selectedAlias;

        if (inputFile == null) {
            onResult.accept(new SignResult(false, null, "יש לבחור קובץ PDF לחתימה."));
            return;
        }
        if (alias == null || !unlockedPasswords.containsKey(alias) || !unlockedCerts.containsKey(alias)) {
            onResult.accept(new SignResult(false, null, "יש לבחור ולפתוח תעודה תקפה לפני החתימה."));
            return;
        }

        char[] password = unlockedPasswords.get(alias);

        new Thread(() -> {
            try {
                String filePath = keyStoreService.getPathForAlias(currentUser.getEmail(), alias);
                KeyAndChain keyAndChain = keyStoreService.getKeyAndChain(filePath, password.clone(), alias);

                SigningStrategy strategy = new Ed25519();
                OcspService ocsp = new OcspService();
                PdfSignatureHandler handler = new PdfSignatureHandler(strategy, keyAndChain.privateKey(), keyAndChain.chain(), ocsp);
                pdfSignerService.signPdf(inputFile, outputFile, handler);

                Platform.runLater(() -> onResult.accept(new SignResult(true, outputFile, null)));
            } catch (Exception ex) {
                Platform.runLater(() -> onResult.accept(new SignResult(false, null, ex.getMessage())));
            } finally {
                try { keyStoreService.wipePassword(password); } catch (Exception ignored) {}
                // מסירים את הסיסמה מה-MAP כדי שלא ינסו להשתמש בה שוב בלי פתיחה חוזרת
                Platform.runLater(() -> unlockedPasswords.remove(alias));
            }
        }, "sign-pdf").start();
    }

    //מוודא שיש מופע של הספק BC
    private void ensureBcProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }


    public record UnlockResult(boolean success, X509Certificate certificate, String errorMessage) {}

    public record SignResult(boolean success, File outputFile, String errorMessage) {}
}