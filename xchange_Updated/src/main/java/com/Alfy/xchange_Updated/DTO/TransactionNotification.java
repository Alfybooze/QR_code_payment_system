package com.Alfy.xchange_Updated.DTO;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TransactionNotification {
    private String timestamp;
    private String amount;
    private String type;
    private String status;
    private String senderName;
    private String receiverName;
}
