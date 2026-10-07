package com.pki.ca.certs.issuer;


import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

//מנהלת באפר משלה
public class DataBuffer {
    private final ByteArrayOutputStream internalBuffer;
    private final UnclosableOutputStream outputStream;

    public DataBuffer() {
        this.internalBuffer = new ByteArrayOutputStream();
        this.outputStream = new UnclosableOutputStream(this.internalBuffer);
    }

    //כתיבה לבאפר
    public OutputStream getOutputStream() {
        return outputStream;
    }

    //מחזירה את הנתונים מהבאפר
    public byte[] toByteArray() {
        return internalBuffer.toByteArray();
    }

}