package com.authentisign.desktop.ui;

import com.authentisign.desktop.security.KeyStore.KeyAndChain;
import com.authentisign.desktop.security.KeyStore.KeyStoreService;
import com.authentisign.desktop.security.strategies.SigningStrategy;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Files;

public class MainController {

    private SigningStrategy strategy;
    private final KeyStoreService ksService = new KeyStoreService();

    public void setStrategy(SigningStrategy strategy) {
        this.strategy = strategy;
    }

    public File signDocument(File fileToSign, String ksPath, char[] pass, String alias) throws Exception {
        byte[] fileData = Files.readAllBytes(fileToSign.toPath());

        KeyAndChain keyAndChain = ksService.getKeyAndChain(ksPath, pass, alias);
        byte[] sigBytes  = strategy.sign(fileData, keyAndChain.privateKey(), keyAndChain.chain());

        File signedFile = new File(fileToSign.getParent(), "signed_" + fileToSign.getName() + ".sig");
        Files.write(signedFile.toPath(), sigBytes);
        return signedFile;
    }


    //פתיחת סייר הקבצים
    public File handleFileSelection(Stage stage){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("בחר קובץ");
        FileChooser.ExtensionFilter pdfFilter = new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf");
        fileChooser.getExtensionFilters().add(pdfFilter);
        //פתיחת חלון
        File selectedFile = fileChooser.showOpenDialog(stage);
        return selectedFile;
    }

}
