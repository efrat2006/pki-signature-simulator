package com.authentisign.desktop.services.signing.ocsp;

import com.authentisign.desktop.client.OcspClient;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import java.math.BigInteger;
import java.security.cert.X509Certificate;



public class OcspService {

    private final OcspClient ocspClient = new OcspClient();
    private static final String BASE_URL = "https://localhost:8080/api/ca/ocsp";

    private final OcspRequestBuilder requestBuilder = new OcspRequestBuilder();
    private final boolean failOpen;

    private final X509Certificate caCertificate;

    public OcspService(){
        this(null, true);
    }

    public OcspService(X509Certificate caCertificate, boolean failOpen) {
        this.caCertificate = caCertificate;
        this.failOpen = failOpen;
    }

    //בודק את תקפות התעודה
    public void verifyForSigning(X509Certificate[] chain) {
        if(chain == null || chain.length == 0){
            throw new RuntimeException("No chain provided");
        }

        X509Certificate cert = chain[0];
        X509Certificate issuer = (chain.length > 1) ? chain[1] : cert;

        OcspResult result = check(cert, issuer);

        switch (result.status()){
            case GOOD:
                return;
            case REVOKED:
                throw new RuntimeException("The certificate was revoked");
            case UNKNOWN:
                throw new RuntimeException("The certificate is unknown - The certificate cannot be verified with the CA");
            case RESPONDER_UNAVAILABLE:
            default:
                if(failOpen)
                    return;
                throw new RuntimeException("The certificate cannot be verified with the CA");
        }
    }


    public OcspResult check(X509Certificate cert, X509Certificate issuer){
        try{
            OCSPReq request = requestBuilder.buildOCSPRequest(issuer, cert.getSerialNumber());

            byte[] responseBytes = ocspClient.post(BASE_URL,request.getEncoded());
            if(responseBytes == null){
                return OcspResult.unavailable("No response from OCSP responder");
            }
            OCSPResp resp = new OCSPResp(responseBytes);
            if (resp.getStatus() != OCSPResp.SUCCESSFUL) {
                return OcspResult.unavailable("OCSP responder status " + resp.getStatus());
            }

            BasicOCSPResp basic = (BasicOCSPResp) resp.getResponseObject();
            if(basic == null){
                return OcspResult.unavailable("Empty OCSP response");
            }

            if(!isSignatureValid(basic, issuer)){
                return OcspResult.unavailable("OCSP response signature is invalid");
            }

            SingleResp single = findForSerial(basic, cert.getSerialNumber());
            if (single == null) {
                return OcspResult.unavailable("No OCSP entry for the requested certificate");
            }

            CertificateStatus status = single.getCertStatus();
            if(status == CertificateStatus.GOOD){
                return OcspResult.good(responseBytes);
            } else if(status instanceof RevokedStatus revoked){
                return OcspResult.revoked(revoked.getRevocationTime(), "Certificate revoked", responseBytes);
            } else{
                return OcspResult.unknown(responseBytes);
            }
        } catch (Exception e){
            return OcspResult.unavailable("OCSP error: " + e.getMessage());
        }
    }


    private boolean isSignatureValid(BasicOCSPResp basic, X509Certificate issuer) throws Exception{
        try{
            X509CertificateHolder verifierCert;
            if(caCertificate != null){
                verifierCert = new JcaX509CertificateHolder(caCertificate);
            }else{
                X509CertificateHolder[] responderCerts = basic.getCerts();
                verifierCert = (responderCerts != null && responderCerts.length > 0)
                        ? responderCerts[0]
                        : new JcaX509CertificateHolder(issuer);
            }

            ContentVerifierProvider cvp = new JcaContentVerifierProviderBuilder()
                    .setProvider("BC")
                    .build(verifierCert);
            return basic.isSignatureValid(cvp);
        } catch(Exception e){
            return false;
        }
    }

    private SingleResp findForSerial(BasicOCSPResp basic, BigInteger serialNumber){
        SingleResp[] responses = basic.getResponses();
        if(responses == null || responses.length == 0)
            return null;

        for (SingleResp r : responses) {
            if (r.getCertID() != null && serialNumber.equals(r.getCertID().getSerialNumber())) {
                return r;
            }
        }
        return responses[0];
    }

}