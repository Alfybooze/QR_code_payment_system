package com.Alfy.xchange_Updated.Services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SmsService {
    @Value("${twilio.account.sid}")
    private String ACCOUNT_SID;

    @Value("${twilio.auth.token}")
    private String AUTH_TOKEN;

    @Value("${twilio.phone.number}")
    private String FROM_NUMBER;

    public void sendTransactionNotification(String toPhoneNumber, String message) {
        try {
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
            Message.creator(
                new PhoneNumber(toPhoneNumber),
                new PhoneNumber(FROM_NUMBER),
                message
            ).create();
            log.info("SMS sent successfully to {}", toPhoneNumber);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage());
        }
    }

    public void notifyTransactionParties(
            String senderPhone, String receiverPhone,
            double amount, double senderBalance, double receiverBalance) {
        
        // Notify sender
        String senderMessage = String.format(
            "You have sent ₦%.2f. Your new balance is ₦%.2f",
            amount, senderBalance
        );
        sendTransactionNotification(senderPhone, senderMessage);

        // Notify receiver
        String receiverMessage = String.format(
            "You have received ₦%.2f. Your new balance is ₦%.2f",
            amount, receiverBalance
        );
        sendTransactionNotification(receiverPhone, receiverMessage);
    }
}