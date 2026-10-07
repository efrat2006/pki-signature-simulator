package com.pki.ca.certs.issuer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

class UnclosableOutputStream extends OutputStream {
    private final ByteArrayOutputStream delegate;
    private boolean closed = false;

    public UnclosableOutputStream(ByteArrayOutputStream delegate) {
        this.delegate = delegate;
    }

    //כתיבת בית אחד לבאפר
    @Override
    public void write(int b) throws IOException {
        if (closed) {
            return;
        }
        delegate.write(b);
    }

    //כתיבת מערך בתים לבאפר
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            return;
        }
        delegate.write(b, off, len);
    }

    //דוחף את הנתונים
    @Override
    public void flush() throws IOException {
        if (!closed) {
            delegate.flush();
        }
    }

    //לא סוגר בפועל
    @Override
    public void close() throws IOException {
        this.closed = true;
    }
}

