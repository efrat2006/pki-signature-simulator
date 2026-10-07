module com.pki.ca {
    opens com.authentisign.desktop.database.entities;

    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.httpserver;
    requires java.net.http;
    requires java.sql;
    requires okhttp3;
    //requires okio;
    requires kotlin.stdlib;

    //בשביל שמירת סיסמאות ב-HASH
    requires jbcrypt;
    requires org.slf4j;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires com.microsoft.sqlserver.jdbc;
    requires jdk.jfr;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires static lombok;
    requires org.apache.pdfbox;
    requires java.desktop;
    requires javafx.swing;
    requires opencv;
    requires com.fasterxml.jackson.databind;
    //requires com.pki.ca;




    opens com.authentisign.desktop.ui to javafx.fxml;
    exports com.authentisign.desktop.ui;

}