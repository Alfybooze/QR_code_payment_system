package com.Alfy.xchange_Updated.Controllers;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import com.Alfy.xchange_Updated.DTO.TransactionNotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/user/{userId}/transaction")
    public void handleTransaction(@Payload TransactionNotification notification, 
                                @DestinationVariable String userId) {
        log.info("Received transaction notification for user {}: {}", userId, notification);
        sendTransactionNotification(userId, notification);
    }

    public void sendTransactionNotification(String userId, TransactionNotification notification) {
        try {
            log.info("Sending notification to user {}: {}", userId, notification);
            
            // Send to user's private queue
            String destination = "/user/" + userId + "/queue/notifications";
            messagingTemplate.convertAndSend(destination, notification);
            
            log.debug("Notification sent successfully to {}", destination);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}", userId, e.getMessage());
        }
    }
}