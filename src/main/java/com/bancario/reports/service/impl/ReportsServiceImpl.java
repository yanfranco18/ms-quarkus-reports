package com.bancario.reports.service.impl;

import com.bancario.reports.client.AccountServiceRestClient;
import com.bancario.reports.client.TransactionsServiceRestClient;
import com.bancario.reports.dto.AccountResponse;
import com.bancario.reports.dto.BalanceReportDTO;
import com.bancario.reports.dto.TransactionResponse;
import com.bancario.reports.enums.ProductType;
import com.bancario.reports.service.ReportsService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class ReportsServiceImpl implements ReportsService {

    @Inject
    @RestClient
    AccountServiceRestClient accountServiceRestClient;

    @Inject
    @RestClient
    TransactionsServiceRestClient transactionsServiceRestClient;

    @Override
    public Uni<List<BalanceReportDTO>> getBalancesByCustomer(String customerId) {
        log.info("Starting report generation for customer with ID: {}", customerId);

        return accountServiceRestClient.getAccountsByCustomer(customerId)
                .onItem().transform(accounts -> {
                    log.info("Found {} accounts for customer ID: {}", accounts.size(), customerId);

                    return accounts.stream()
                            .map(this::mapToBalanceReportDTO)
                            .collect(Collectors.toList());
                });
    }

    @Override
    public Uni<List<TransactionResponse>> getTransactionsByAccountId(String accountId) {
        log.info("Starting transaction report for account ID: {}", accountId);

        // Se llama al cliente REST para obtener los movimientos.
        return transactionsServiceRestClient.getTransactionsByAccountId(accountId)
                .onItem().invoke(transactions -> {
                    log.info("Found {} transactions for account ID: {}", transactions.size(), accountId);
                });
    }

    private BalanceReportDTO mapToBalanceReportDTO(AccountResponse account) {
        log.debug("Mapping account {} with type {}", account.id(), account.productType());

        BigDecimal availableBalance;
        if (account.productType() == ProductType.ACTIVE) {
            // Verifica si amountUsed es nulo y, si lo es, usa BigDecimal.ZERO.
            BigDecimal amountUsed = (account.amountUsed() != null) ? account.amountUsed() : BigDecimal.ZERO;
            availableBalance = account.balance().subtract(amountUsed);
            log.debug("Calculated active product balance: {}", availableBalance);
        } else {
            availableBalance = account.balance();
            log.debug("Calculated passive product balance: {}", availableBalance);
        }

        return new BalanceReportDTO(
                account.id(),
                account.productType(),
                availableBalance
        );
    }
}