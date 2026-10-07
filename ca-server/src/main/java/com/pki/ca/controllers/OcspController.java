package com.pki.ca.controllers;

import com.pki.ca.certs.ca.CaKeyService;
import com.pki.ca.services.OcspService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/ca/ocsp")
@CrossOrigin(origins = "*")
public class OcspController {

    private static final Logger logger = LoggerFactory.getLogger(OcspController.class);

    private final OcspService ocspService;
    private final CaKeyService caKeyService;

    public OcspController(OcspService ocspService, CaKeyService caKeyService) {
        this.ocspService = ocspService;
        this.caKeyService = caKeyService;
    }

    @PostMapping(
            consumes = "application/ocsp-request",
            produces = "application/ocsp-response")
    public ResponseEntity<byte[]> ocspRequest(@RequestBody byte[] requestBytes){
        try{
            byte[] response = ocspService.processRequest(requestBytes);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/ocsp-response"))
                    .body(response);
        } catch (Exception e){
            logger.error("OCSP request failed {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        }
    }



    @GetMapping(value = "/ca-certificate", produces = "application/pkix-cert")
    public ResponseEntity<byte[]> getCaCertificate() {
        try {
            byte[] encoded = caKeyService.getCaCertificate().getEncoded();
            return ResponseEntity.ok().body(encoded);
        } catch (Exception e) {
            logger.error("Failed to return CA certificate: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
