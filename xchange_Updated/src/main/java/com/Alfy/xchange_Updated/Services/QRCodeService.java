package com.Alfy.xchange_Updated.Services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class QRCodeService {
    
    // Set a default expiration time (15 minutes)
    private static final int DEFAULT_EXPIRATION_MINUTES = 15;
    
    private final ObjectMapper objectMapper;
    
    public byte[] generateQRCode(Double totalAmount, Long userId, String username, String currency) throws WriterException, IOException {
        // Create QR code data with expiration time
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expirationTime = currentTime.plusMinutes(DEFAULT_EXPIRATION_MINUTES);
        
        // Using HashMap instead of Map.of() to allow more flexibility
        Map<String, Object> qrData = new HashMap<>();
        qrData.put("totalAmount", totalAmount);
        qrData.put("currency", currency);
        qrData.put("userId", userId);
        qrData.put("username", username);
        qrData.put("timestamp", currentTime.toString());
        qrData.put("expirationTime", expirationTime.toString());
        qrData.put("type", "PAYMENT_REQUEST");

        // Convert data to JSON string
        String qrCodeData = objectMapper.writeValueAsString(qrData);

        // Create QR Code
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(
            qrCodeData,
            BarcodeFormat.QR_CODE,
            200,  // width
            200   // height
        );

        // Convert to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        return outputStream.toByteArray();
    }

    public String processPayment(String qrCodeData) {
        try {
            // Parse QR code data
            Map<String, Object> qrData = objectMapper.readValue(qrCodeData, Map.class);
            
            // Check if QR code has expired
            if (qrData.containsKey("expirationTime")) {
                String expirationTimeStr = qrData.get("expirationTime").toString();
                boolean isExpired = isQrCodeExpired(expirationTimeStr);
                if (isExpired) {
                    throw new RuntimeException("QR code has expired. Please request a new QR code.");
                }
            }
            
            Double amount = Double.valueOf(qrData.get("totalAmount").toString());
            String timestamp = qrData.get("timestamp").toString();
            Long userId = Long.valueOf(qrData.get("userId").toString());
            String username = qrData.get("username").toString();
            
            // Generate receipt
            return String.format(
                "Receipt:\nPaid to: %s (ID: %d)\nAmount: $%.2f\nTimestamp: %s",
                username,
                userId,
                amount,
                timestamp
            );
        } catch (Exception e) {
            log.error("Error processing payment: ", e);
            throw new RuntimeException("Failed to process payment: " + e.getMessage(), e);
        }
    }
    
    // Helper method to check if QR code has expired
    public boolean isQrCodeExpired(String expirationTimeStr) {
        try {
            LocalDateTime expirationTime = LocalDateTime.parse(expirationTimeStr);
            return LocalDateTime.now().isAfter(expirationTime);
        } catch (Exception e) {
            // If parsing fails, consider it expired for security
            log.error("Error parsing expiration time: ", e);
            return true;
        }
    }
}