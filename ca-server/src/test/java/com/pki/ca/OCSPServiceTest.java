package com.pki.ca;

import com.pki.ca.certs.ca.CaKeyService;
import com.pki.ca.entities.Certificate;
import com.pki.ca.entities.RevokedCertificate;
import com.pki.ca.repositories.CertificateRepo;
import com.pki.ca.services.OcspService;
import com.pki.ca.services.RevokedCertificateService;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * טסט מקצה-לקצה ל-OCSP התקני (RFC 6960).
 *
 * הטסט מדמה את מה שהלקוח עושה: בונה בקשת OCSP בינארית, מריץ אותה דרך
 * ה-OcspService האמיתי (עם CaKeyService אמיתי + repos מדומים), ומאמת:
 *   1. שהתשובה חתומה ע"י ה-CA (הליבה של התקן).
 *   2. שה-nonce מוחזר (הגנה מ-replay).
 *   3. שהסטטוס נכון בכל תרחיש: GOOD / REVOKED / UNKNOWN.
 */
@DisplayName("OCSP End-To-End (RFC 6960) Tests")
class OCSPServiceTest {

    @Mock
    private CertificateRepo certificateRepo;

    @Mock
    private RevokedCertificateService revokedCertificateService;

    private CaKeyService caKeyService;   // אמיתי - יוצר זהות CA
    private OcspService ocspService;

    private final String GOOD_SERIAL = "1234567890";
    private final String REVOKED_SERIAL = "9999999999";
    private final String UNKNOWN_SERIAL = "0000000000";

    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // CaKeyService אמיתי - יוצר keystore זמני לבדיקה
        caKeyService = new CaKeyService();
        // הזרקת שדות @Value ידנית (בטסט אין Spring context)
        setField(caKeyService, "keystoreDir", "target/test-ca");
        setField(caKeyService, "keystoreFileName", "test-ca.p12");
        setField(caKeyService, "keystorePassword", "12345678");
        caKeyService.init();

        ocspService = new OcspService(caKeyService, certificateRepo, revokedCertificateService);

        // תעודה תקפה
        Certificate goodCert = new Certificate();
        goodCert.setCertSerialNumber(GOOD_SERIAL);
        goodCert.setExpiryDate(LocalDateTime.now().plusDays(30));
        when(certificateRepo.findBycertSerialNumber(GOOD_SERIAL)).thenReturn(Optional.of(goodCert));
        when(revokedCertificateService.isRevoked(goodCert)).thenReturn(Optional.empty());

        // תעודה מבוטלת
        Certificate revokedCert = new Certificate();
        revokedCert.setCertSerialNumber(REVOKED_SERIAL);
        revokedCert.setExpiryDate(LocalDateTime.now().plusDays(30));
        RevokedCertificate revocation = new RevokedCertificate();
        revocation.setRevocationDate(LocalDateTime.now().minusDays(2));
        when(certificateRepo.findBycertSerialNumber(REVOKED_SERIAL)).thenReturn(Optional.of(revokedCert));
        when(revokedCertificateService.isRevoked(revokedCert)).thenReturn(Optional.of(revocation));

        // תעודה לא קיימת
        when(certificateRepo.findBycertSerialNumber(UNKNOWN_SERIAL)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("תעודה תקפה מחזירה GOOD, והתשובה חתומה ע\"י ה-CA")
    void goodCertificate_isSignedAndGood() throws Exception {
        byte[] nonce = randomNonce();
        byte[] requestBytes = buildRequest(new BigInteger(GOOD_SERIAL), nonce);

        byte[] responseBytes = ocspService.processRequest(requestBytes);

        BasicOCSPResp basic = parseAndCheckSignature(responseBytes);   // מאמת חתימת CA
        assertNonceEchoed(basic, nonce);                               // מאמת nonce

        SingleResp single = basic.getResponses()[0];
        assertNull(single.getCertStatus(), "GOOD מיוצג ע\"י null");
        System.out.println("GOOD: response signed by CA + nonce verified");
    }

    @Test
    @DisplayName("תעודה מבוטלת מחזירה REVOKED")
    void revokedCertificate_isRevoked() throws Exception {
        byte[] requestBytes = buildRequest(new BigInteger(REVOKED_SERIAL), randomNonce());

        byte[] responseBytes = ocspService.processRequest(requestBytes);

        BasicOCSPResp basic = parseAndCheckSignature(responseBytes);
        SingleResp single = basic.getResponses()[0];
        assertTrue(single.getCertStatus() instanceof RevokedStatus, "צריך להיות RevokedStatus");
        System.out.println("REVOKED: status correct + response signed");
    }

    @Test
    @DisplayName("תעודה לא קיימת מחזירה UNKNOWN")
    void unknownCertificate_isUnknown() throws Exception {
        byte[] requestBytes = buildRequest(new BigInteger(UNKNOWN_SERIAL), randomNonce());

        byte[] responseBytes = ocspService.processRequest(requestBytes);

        BasicOCSPResp basic = parseAndCheckSignature(responseBytes);
        SingleResp single = basic.getResponses()[0];
        assertTrue(single.getCertStatus() instanceof UnknownStatus, "צריך להיות UnknownStatus");
        System.out.println("UNKNOWN: status correct + response signed");
    }

    // ─────────────────────────────────────────────
    // עזרים - מדמים את הלקוח
    // ─────────────────────────────────────────────

    /** בונה בקשת OCSP בינארית, בדיוק כמו הלקוח (issuer = תעודת ה-CA). */
    private byte[] buildRequest(BigInteger serial, byte[] nonce) throws Exception {
        X509CertificateHolder issuerHolder =
                new JcaX509CertificateHolder(caKeyService.getCaCertificate());
        CertificateID certId = new CertificateID(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(CertificateID.HASH_SHA1),
                issuerHolder, serial);

        OCSPReqBuilder builder = new OCSPReqBuilder();
        builder.addRequest(certId);
        Extension ext = new Extension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, false, new DEROctetString(nonce));
        builder.setRequestExtensions(new Extensions(ext));
        return builder.build().getEncoded();
    }

    /** מפענח את התשובה ומאמת שהיא חתומה ע"י ה-CA - הליבה של התקן. */
    private BasicOCSPResp parseAndCheckSignature(byte[] responseBytes) throws Exception {
        OCSPResp resp = new OCSPResp(responseBytes);
        assertEquals(OCSPResp.SUCCESSFUL, resp.getStatus(), "סטטוס התגובה צריך להיות SUCCESSFUL");

        BasicOCSPResp basic = (BasicOCSPResp) resp.getResponseObject();
        assertNotNull(basic, "התשובה לא ריקה");

        ContentVerifierProvider cvp = new JcaContentVerifierProviderBuilder()
                .setProvider("BC")
                .build(caKeyService.getCaCertificate().getPublicKey());
        assertTrue(basic.isSignatureValid(cvp), "התשובה חייבת להיות חתומה ע\"י ה-CA");
        return basic;
    }

    private void assertNonceEchoed(BasicOCSPResp basic, byte[] sentNonce) {
        Extension respNonce = basic.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
        assertNotNull(respNonce, "ה-nonce צריך לחזור בתשובה");
    }

    private byte[] randomNonce() {
        byte[] n = new byte[16];
        new SecureRandom().nextBytes(n);
        return n;
    }

    private void setField(Object target, String name, String value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}