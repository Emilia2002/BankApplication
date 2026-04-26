package com.bankapplication.management.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

// Controller for security purposes
@Controller
public class AdvancedSecurityController {

    @Value("${app.redirect.aes-secret:MySuperSecretBankKey123}")
    private String aesSecretKey;

    @GetMapping("/secure-redirect")
    public RedirectView handleRedirect(@RequestParam("target") String encryptedTarget) {
        try {
            byte[] keyBytes = aesSecretKey.substring(0, 16).getBytes("UTF-8");
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decodedBytes = Base64.getUrlDecoder().decode(encryptedTarget);
            String decryptedUrl = new String(cipher.doFinal(decodedBytes));

            return new RedirectView(decryptedUrl);
        } catch (Exception e) {
            return new RedirectView("/error");
        }
    }

    @GetMapping("/api/transfers/receipt")
    public ResponseEntity<String> generateReceipt(HttpServletRequest request) {
        String host = request.getHeader("Host");

        String cssUrl = "http://" + host + "/assets/receipt-styles.css";

        try {
            RestTemplate restTemplate = new RestTemplate();
            String cssContent = restTemplate.getForObject(cssUrl, String.class);
            return ResponseEntity.ok("PDF Generated with external CSS length: " + cssContent.length());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching CSS: " + e.getMessage());
        }
    }
}