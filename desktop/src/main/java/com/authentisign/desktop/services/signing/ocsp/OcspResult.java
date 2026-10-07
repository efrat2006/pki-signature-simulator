package com.authentisign.desktop.services.signing.ocsp;

import java.util.Date;



//תוצאת הביטול, פרטי ביטול ותשובת הOCSP
public record OcspResult(
        OcspStatus status,
        Date revocationTime,
        String reasonText,
        byte[] rawResponse
) {
    public boolean isGood()    { return status == OcspStatus.GOOD; }
    public boolean isRevoked() { return status == OcspStatus.REVOKED; }

    static OcspResult good(byte[] raw) {
        return new OcspResult(OcspStatus.GOOD, null, "Certificate is valid", raw);
    }
    static OcspResult revoked(Date time, String reason, byte[] raw) {
        return new OcspResult(OcspStatus.REVOKED, time, reason, raw);
    }
    static OcspResult unknown(byte[] raw) {
        return new OcspResult(OcspStatus.UNKNOWN, null, "Certificate status unknown", raw);
    }
    static OcspResult unavailable(String reason) {
        return new OcspResult(OcspStatus.RESPONDER_UNAVAILABLE, null, reason, null);
    }
}