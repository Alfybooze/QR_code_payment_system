 package com.Alfy.xchange_Updated.Controllers;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import com.Alfy.xchange_Updated.DTO.TransactionNotification;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/transaction")
    @SendTo("/topic/transactions")
    public TransactionNotification handleTransaction(@Payload TransactionNotification notification) {
        log.info("Received transaction notification: {}", notification);
        return notification;
    }

    public void sendTransactionNotification(String username, TransactionNotification notification) {
        log.info("Sending notification to user {}: {}", username, notification);
        messagingTemplate.convertAndSendToUser(
            username,
            "/queue/notifications",
            notification
        );
    }

    public void broadcastTransactionUpdate(TransactionNotification notification) {
        log.info("Broadcasting transaction update: {}", notification);
        messagingTemplate.convertAndSend("/topic/transactions", notification);
    }
}