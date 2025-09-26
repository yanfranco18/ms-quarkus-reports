package com.bancario.reports.client;

import com.bancario.reports.dto.AccountResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "account-service")
@Produces(MediaType.APPLICATION_JSON)
@Path("/accounts")
public interface AccountServiceRestClient {

    @GET
    Uni<List<AccountResponse>> getAccountsByCustomer(@QueryParam("customerId") String customerId);
}