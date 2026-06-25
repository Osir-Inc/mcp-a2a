package com.osir.mcp.clients;

import com.osir.mcp.models.transfer.*;
import com.osir.mcp.models.transfer.PendingTransferListApiResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface TransferBackendClient {

    @GET
    @Path("/v2/transfer/{domain}/quote")
    TransferQuoteResponse getTransferQuote(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/transfer/initiate")
    TransferInitiateResponse initiateTransfer(
            TransferInitiateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v2/transfer/{domain}/status")
    TransferStatusResponse getTransferStatus(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/transfer/{domain}/cancel")
    TransferActionResponse cancelTransfer(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v2/transfer/gaining/pending")
    PendingTransferListApiResponse listPendingTransfers(
            @HeaderParam("Authorization") String bearerToken
    );
}
