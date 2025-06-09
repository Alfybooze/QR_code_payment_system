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
// In WebSocketService
public void notifyTransactionUpdate(String userId, TransactionNotification notification) {
    try {
        log.info("Sending user-specific notification to {}: {}", userId, notification);
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/notifications", // This is the primary user-specific channel
            notification
        );
    } catch (Exception e) {
        log.error("Error sending WebSocket notification to user {}: {}", userId, e.getMessage());
    }
}
}