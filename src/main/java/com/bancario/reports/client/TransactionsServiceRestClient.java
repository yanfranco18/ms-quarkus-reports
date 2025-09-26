package com.bancario.reports.client;

import com.bancario.reports.dto.TransactionResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/transactions")
@RegisterRestClient(configKey = "transactionsService")
public interface TransactionsServiceRestClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<TransactionResponse>> getTransactionsByAccountId(@QueryParam("accountId") String accountId);
}