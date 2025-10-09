package com.bancario.reports.service.impl;

import com.bancario.reports.client.AccountServiceRestClient;
import com.bancario.reports.client.TransactionsServiceRestClient;
import com.bancario.reports.dto.*;
import com.bancario.reports.enums.ProductType;
import com.bancario.reports.service.ReportsService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    @Override
    public Uni<List<CommissionReportItem>> generateCommissionsReport(LocalDate startDate, LocalDate endDate) {

        log.info("SERVICE | Iniciando generación de reporte para rango: {} a {}", startDate, endDate);

        // 1. Orquestación: Consumir el Transaction-Service para obtener los datos detallados
        return transactionsServiceRestClient.getCommissionsReportData(startDate, endDate)

                // 2. Lógica de negocio: Aplicar la agregación (Manejo de Responsabilidad Única)
                .onItem().transform(this::aggregateCommissions)

                .onFailure().invoke(e -> {
                    log.error("SERVICE | Fallo al obtener o agregar datos de comisiones: {}", e.getMessage(), e);
                })
                .onFailure().transform(e ->
                        new RuntimeException("Error en la fuente de datos (Transaction Service).", e)
                );
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

    /**
     * Aplica la lógica de GroupBy y Sum para transformar la lista detallada en el reporte final.
     * @param detailedCommissions Lista de comisiones brutas recibidas del Transaction-Service.
     * @return Lista de CommissionReportItem sumados.
     */
    private List<CommissionReportItem> aggregateCommissions(List<CommissionReportDto> detailedCommissions) {

        if (detailedCommissions.isEmpty()) {
            log.info("SERVICE | No se encontraron comisiones en el rango especificado.");
            return List.of();
        }

        log.debug("SERVICE | {} registros detallados recibidos. Iniciando agregación.", detailedCommissions.size());

        // A. Agrupar los DTOs por productName
        Map<String, List<CommissionReportDto>> groupedByProductName = detailedCommissions.stream()
                .collect(Collectors.groupingBy(CommissionReportDto::productName));

        // B. Mapear y Sumar cada grupo
        return groupedByProductName.entrySet().stream()
                .map(entry -> {
                    String productName = entry.getKey();
                    List<CommissionReportDto> list = entry.getValue();

                    // Sumar las comisiones de todos los items en este grupo
                    BigDecimal totalFees = list.stream()
                            .map(CommissionReportDto::fee)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Extraer ProductType (asumiendo consistencia dentro del grupo)
                    ProductType productType = list.getFirst().productType();

                    return CommissionReportItem.builder()
                            .productName(productName)
                            .productType(productType)
                            .totalFees(totalFees)
                            .build();
                })
                .collect(Collectors.toList());
    }
}