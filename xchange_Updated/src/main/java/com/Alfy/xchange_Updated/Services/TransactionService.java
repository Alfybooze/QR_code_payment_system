package com.Alfy.xchange_Updated.Services;

import com.Alfy.xchange_Updated.Models.Users;
import com.Alfy.xchange_Updated.Models.Transaction;
import com.Alfy.xchange_Updated.Repositories.UsersRepository;
import com.Alfy.xchange_Updated.Repositories.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    public static final String TRANSACTION_TYPE_OUTGOING = "OUTGOING";
    public static final String TRANSACTION_TYPE_INCOMING = "INCOMING";
    public static final String TRANSACTION_STATUS_COMPLETED = "COMPLETED";

    private final TransactionRepository transactionRepository;
    private final UsersRepository usersRepository;

    @Transactional
    public Transaction processPayment(Long buyerId, Long sellerId, Double amount, String currency) {
        log.info("Processing payment: {} {} from buyer {} to seller {}", amount, currency, buyerId, sellerId);
        
        try {
            Users buyer = usersRepository.getUsersById(buyerId)
                .orElseThrow(() -> new RuntimeException("Buyer not found"));
            Users seller = usersRepository.getUsersById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

            Transaction transaction = Transaction.builder()
                .buyer(buyer)
                .seller(seller)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .status(TRANSACTION_STATUS_COMPLETED)
                .type(TRANSACTION_TYPE_OUTGOING) // Default to outgoing from buyer's perspective
                .build();

            Transaction savedTransaction = transactionRepository.save(transaction);
            log.info("Transaction saved successfully with ID: {}", savedTransaction.getId());
            
            return savedTransaction;
        } catch (Exception e) {
            log.error("Failed to process payment: ", e);
            throw new RuntimeException("Payment processing failed: " + e.getMessage());
        }
    }

    public List<Transaction> getTransactionHistory(Long userId) {
        return transactionRepository.findByBuyerIdOrSellerId(userId, userId);
    }

    public Double getIncomingTotal(Long userId) {
        try {
            Double total = transactionRepository.getTotalIncoming(userId);
            log.info("Total incoming amount for user {}: {}", userId, total);
            return total;
        } catch (Exception e) {
            log.error("Error calculating incoming total for user {}: {}", userId, e.getMessage());
            return 0.0;
        }
    }

    public Double getOutgoingTotal(Long userId) {
        try {
            Double total = transactionRepository.getTotalOutgoing(userId);
            log.info("Total outgoing amount for user {}: {}", userId, total);
            return total;
        } catch (Exception e) {
            log.error("Error calculating outgoing total for user {}: {}", userId, e.getMessage());
            return 0.0;
        }
    }
    public Map<String, Object> getTransactionChartData(Long userId) {
    LocalDateTime endDate = LocalDateTime.now();
    LocalDateTime startDate = endDate.minusDays(7);
    
    List<Transaction> transactions = transactionRepository.findTransactionsForChart(
        userId, 
        startDate, 
        endDate
    );

    // Group transactions by date
    Map<String, Double> dailyTotals = transactions.stream()
        .collect(Collectors.groupingBy(
            transaction -> transaction.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM")),
            Collectors.summingDouble(Transaction::getAmount)
        ));

    // Create sorted lists for labels and data
    List<String> labels = new ArrayList<>();
    List<Double> data = new ArrayList<>();

    // Fill in missing dates with zeros
    for (int i = 6; i >= 0; i--) {
        String date = endDate.minusDays(i).format(DateTimeFormatter.ofPattern("dd MMM"));
        labels.add(date);
        data.add(dailyTotals.getOrDefault(date, 0.0));
    }

    Map<String, Object> chartData = new HashMap<>();
    chartData.put("labels", labels);
    chartData.put("data", data);
    
    return chartData;
}
    
}
