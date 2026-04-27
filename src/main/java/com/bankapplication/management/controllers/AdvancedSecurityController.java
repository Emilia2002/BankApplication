package com.bankapplication.management.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

// Controller for security purposes
@Controller
public class AdvancedSecurityController {
    // get the secret key from application properties
    @Value("${app.redirect.aes-secret}")
    private String aesSecretKey;

    // endpoint to handle secure redirects with encrypted target URLs
    @GetMapping("/secure-redirect")
    public RedirectView handleRedirect(@RequestParam("target") String encryptedTarget) {
        try {
            // Advanced encryption handling for secure redirects
            byte[] keyBytes = aesSecretKey.substring(0, 16).getBytes("UTF-8");

            // generate the secret key and initialize the decryption
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // decrypt the target URL using AES encryption
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            // decode the encrypted target URL from Base64 and decrypt it
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encryptedTarget);

            // extract the decrypted URL and redirect to it
            String decryptedUrl = new String(cipher.doFinal(decodedBytes));

            // trust the decrypted URL and redirect to it
            return new RedirectView(decryptedUrl);
        } catch (Exception e) {
            // else throw an error and redirect to a safe error page
            return new RedirectView("/error");
        }
    }

    @GetMapping("/api/transfers/receipt")
    public ResponseEntity<?> generateReceipt(HttpServletRequest request) {
        String host = request.getHeader("Host");
        String cssUrl = "http://" + host + "/assets/receipt-styles.css";

        try {
            // create REQUEST to fetch the CSS content from the specified URL
            RestTemplate restTemplate = new RestTemplate();
            String cssContent = restTemplate.getForObject(cssUrl, String.class);

            // create a simple HTML template for the receipt, embedding the fetched CSS content
            String htmlTemplate = "<html><head><style>" + cssContent + "</style></head>" +
                    "<body>" +
                    "<h1>Chitanta Transfer Bancar</h1>" +
                    "<p>Suma: <strong>$500.00</strong></p>" +
                    "<p>Status: <span style='color:green;'>Completat</span></p>" +
                    "</body></html>";

            // generate the pdf from the HTML template using Flying Saucer
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlTemplate);
            renderer.layout();
            renderer.createPDF(outputStream);

            byte[] pdfBytes = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);

            headers.add("Content-Disposition", "inline; filename=receipt.pdf");

            return new ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching CSS or generating PDF: " + e.getMessage());
        }
    }
}