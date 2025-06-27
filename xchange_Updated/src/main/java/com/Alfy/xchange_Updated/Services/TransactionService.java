package com.Alfy.xchange_Updated.Services;

import com.Alfy.xchange_Updated.Models.Users;
import com.Alfy.xchange_Updated.DTO.TransactionNotification;
import com.Alfy.xchange_Updated.Models.Transaction;
import com.Alfy.xchange_Updated.Repositories.UsersRepository;
import com.Alfy.xchange_Updated.Repositories.TransactionRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.YearMonth;
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
     private final WebSocketService webSocketService;

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
            notifyTransactionParties(savedTransaction);
            return savedTransaction;
        } catch (Exception e) {
            log.error("Failed to process payment: ", e);
            throw new RuntimeException("Payment processing failed: " + e.getMessage());
        }
    }
      private void notifyTransactionParties(Transaction transaction) {
        // Notify buyer
        TransactionNotification buyerNotification = createNotification(transaction, TRANSACTION_TYPE_OUTGOING);
        webSocketService.notifyTransactionUpdate(String.valueOf(transaction.getBuyer().getId()), buyerNotification);

        // Notify seller
        TransactionNotification sellerNotification = createNotification(transaction, TRANSACTION_TYPE_INCOMING);
        webSocketService.notifyTransactionUpdate(String.valueOf(transaction.getSeller().getId()), sellerNotification);
    }
    public List<Transaction> getTransactionHistory(Long userId, int limit) {
        return transactionRepository.findRecentTransactions(userId, limit);
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
      private TransactionNotification createNotification(Transaction transaction, String type) {
        TransactionNotification notification = new TransactionNotification();
        notification.setTimestamp(transaction.getTimestamp().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
        notification.setAmount(String.format("%.2f", transaction.getAmount()));
        notification.setType(type);
        notification.setStatus(transaction.getStatus());
        notification.setSenderName(transaction.getBuyer().getUsername());
        notification.setReceiverName(transaction.getSeller().getUsername());
        return notification;
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
    public Page<Transaction> getTransactionsByUserAndMonth(Long userId, int year, int month, Pageable pageable) {
        // Validate month parameter
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        // Create start of month (first day at 00:00:00)
        LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0, 0);
        
        // Create end of month (last day at 23:59:59.999999999)
        LocalDateTime endDate = startDate.plusMonths(1).minusNanos(1);

        return transactionRepository.findAllByUserIdAndMonth(userId, startDate, endDate, pageable);
    }


    public Page<Transaction> getTransactionsByUserAndYear(Long userId, int year, Pageable pageable) {
    // Create start of year (January 1st at 00:00:00)
    LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0, 0);
    
    // Create end of year (December 31st at 23:59:59.999999999)
    LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59, 999999999);
    
    return transactionRepository.findAllByUserIdAndMonth(userId, startDate, endDate, pageable);
}
    /**
     * Overloaded method that accepts YearMonth for easier usage
     * @param userId The ID of the user
     * @param yearMonth The year-month combination
     * @param pageable Pagination information
     * @return Page of transactions for the specified month
     */
    public Page<Transaction> getTransactionsByUserAndMonth(Long userId, YearMonth yearMonth, Pageable pageable) {
        return getTransactionsByUserAndMonth(userId, yearMonth.getYear(), yearMonth.getMonthValue(), pageable);
    }

    /**
     * Get transactions for current month with pagination
     * @param userId The ID of the user
     * @param pageable Pagination information
     * @return Page of transactions for current month
     */
public Page<Transaction> getCurrentMonthTransactions(Long userId, Pageable pageable) {
    YearMonth currentMonth = YearMonth.now();
    return getTransactionsByUserAndMonth(userId, currentMonth.getYear(), currentMonth.getMonthValue(), pageable);
}

    /**
     * Get transactions for previous month with pagination
     * @param userId The ID of the user
     * @param pageable Pagination information
     * @return Page of transactions for previous month
     */
    public Page<Transaction> getPreviousMonthTransactions(Long userId, Pageable pageable) {
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        return getTransactionsByUserAndMonth(userId, previousMonth, pageable);
    }

    /**
     * Convenience method with default pagination (20 items per page, sorted by timestamp desc)
     * @param userId The ID of the user
     * @param year The year
     * @param month The month
     * @return Page of transactions with default pagination
     */
    public Page<Transaction> getTransactionsByUserAndMonth(Long userId, int year, int month) {
        Pageable defaultPageable = PageRequest.of(0, 20, Sort.by("timestamp").descending());
        return getTransactionsByUserAndMonth(userId, year, month, defaultPageable);
    }
    // Add this method to your TransactionService
public Page<Transaction> getAllTransactionsByUser(Long userId, Pageable pageable) {
    return transactionRepository.findAllByUserId(userId, pageable);
}
}
