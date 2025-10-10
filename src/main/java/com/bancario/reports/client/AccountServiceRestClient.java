package com.bancario.reports.client;

import com.bancario.reports.dto.AccountResponse;
import com.bancario.reports.dto.DailyBalanceHistoryDto;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.LocalDate;
import java.util.List;

@RegisterRestClient(configKey = "account-service")
@Produces(MediaType.APPLICATION_JSON)
@Path("/accounts")
public interface AccountServiceRestClient {

    @GET
    Uni<List<AccountResponse>> getAccountsByCustomer(@QueryParam("customerId") String customerId);

    /**
     * Obtiene de forma asíncrona el historial de saldos diarios (EOD) para todos los productos
     * de un cliente dentro de un rango de fechas.
     * * @param customerId El ID único del cliente para quien se solicitan los saldos.
     * @param startDate La fecha de inicio del periodo de consulta (YYYY-MM-DD).
     * @param endDate La fecha de fin del periodo de consulta (YYYY-MM-DD).
     * @return Uni que emite una lista de DailyBalanceHistoryDto con los saldos diarios.
     */
    @GET
    @Path("/daily-balances")
    Uni<List<DailyBalanceHistoryDto>> getDailyBalancesByCustomer(
            @QueryParam("customerId") String customerId,
            @QueryParam("startDate") LocalDate startDate,
            @QueryParam("endDate") LocalDate endDate
    );
}