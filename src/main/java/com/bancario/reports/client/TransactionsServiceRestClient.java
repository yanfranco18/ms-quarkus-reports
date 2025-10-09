package com.bancario.reports.client;

import com.bancario.reports.dto.CommissionReportDto;
import com.bancario.reports.dto.TransactionResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.LocalDate;
import java.util.List;

@Path("/transactions")
@RegisterRestClient(configKey = "transactionsService")
public interface TransactionsServiceRestClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<TransactionResponse>> getTransactionsByAccountId(@QueryParam("accountId") String accountId);

    /**
     * Llama al endpoint del Transaction-Service para obtener el detalle de comisiones.
     * @param startDate La fecha de inicio del periodo.
     * @param endDate La fecha de fin del periodo.
     * @return Uni que emite una lista de CommissionReportDto.
     */
    @GET
    @Path("/commissions")
    Uni<List<CommissionReportDto>> getCommissionsReportData(
            @QueryParam("startDate") LocalDate startDate,
            @QueryParam("endDate") LocalDate endDate
    );
}