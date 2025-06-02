package com.Alfy.xchange_Updated.Services;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.Alfy.xchange_Updated.DTO.TransactionNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;

    public void notifyTransactionUpdate(String userId, TransactionNotification notification) {
        try {
            log.info("Sending notification to user {}: {}", userId, notification);
            messagingTemplate.convertAndSend("/topic/transactions/" + userId, notification);
        } catch (Exception e) {
            log.error("Error sending WebSocket notification: ", e);
        }
    }
}