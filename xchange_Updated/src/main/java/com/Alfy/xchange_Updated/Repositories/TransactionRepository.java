package com.Alfy.xchange_Updated.Repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Alfy.xchange_Updated.Models.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    List<Transaction> findByBuyerIdOrSellerId(Long buyerId, Long sellerId);

    @Query("SELECT t FROM Transaction t WHERE (t.buyer.id = :userId OR t.seller.id = :userId) " +
           "ORDER BY t.timestamp DESC LIMIT :limit")
    List<Transaction> findRecentTransactions(@Param("userId") Long userId, @Param("limit") int limit);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.seller.id = :sellerId " +
           "AND t.timestamp BETWEEN :startDate AND :endDate AND t.status = 'COMPLETED'")
    Double calculateTotalIncome(@Param("sellerId") Long sellerId, 
                              @Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.buyer.id = :buyerId " +
           "AND t.timestamp BETWEEN :startDate AND :endDate AND t.status = 'COMPLETED'")
    Double calculateTotalSpent(@Param("buyerId") Long buyerId,
                             @Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate);
                             
    @Query("SELECT t FROM Transaction t WHERE t.seller.id = :sellerId AND t.status = 'COMPLETED'")
    List<Transaction> getSellerId(@Param("sellerId") Long sellerId);
                             
    @Query("SELECT t FROM Transaction t WHERE t.buyer.id = :buyerId AND t.status = 'COMPLETED'")
    List<Transaction> findByBuyerId(@Param("buyerId") Long buyerId);
                             
    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t " +
          "WHERE t.seller.id = :userId AND t.status = 'COMPLETED'")
    Double getTotalIncoming(@Param("userId") Long userId);
                             
    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t " +
           "WHERE t.buyer.id = :userId AND t.status = 'COMPLETED'")
    Double getTotalOutgoing(@Param("userId") Long userId);
    @Query("SELECT t FROM Transaction t WHERE (t.buyer.id = :userId OR t.seller.id = :userId) " +
       "AND t.timestamp BETWEEN :startDate AND :endDate " +
       "ORDER BY t.timestamp DESC")
List<Transaction> findTransactionsForChart(
    @Param("userId") Long userId,
    @Param("startDate") LocalDateTime startDate,
    @Param("endDate") LocalDateTime endDate
);

}