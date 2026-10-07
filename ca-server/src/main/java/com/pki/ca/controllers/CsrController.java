package com.pki.ca.controllers;

import com.pki.ca.certs.validation.CsrValidator;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/ca/csr")
public class CsrController {

    @Autowired
    private CsrValidator csrValidator;

    @PostMapping("/sumbit")
    public ResponseEntity<String> validateCSR(@RequestBody String csrPem) {
        try{
            PKCS10CertificationRequest csr = csrValidator.parseCsr(csrPem);
            csrValidator.validate(csr);

            return ResponseEntity.ok("Success");
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed");
        }

    }
}
