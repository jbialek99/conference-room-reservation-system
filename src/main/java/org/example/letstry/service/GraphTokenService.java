package org.example.letstry.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GraphTokenService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;

    public GraphTokenService(
            WebClient.Builder webClientBuilder,
            @Value("${azure.client-id}") String clientId,
            @Value("${azure.client-secret}") String clientSecret,
            @Value("${azure.tenant-id}") String tenantId
    ) {
        this.webClient = webClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
    }

    public String getAppAccessToken() {
        try {
            JsonNode response = webClient.post()
                    .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", tenantId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("scope", "https://graph.microsoft.com/.default"))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                throw new GraphIntegrationException("Brak odpowiedzi serwera tokenow.");
            }

            String token = response.path("access_token").asText("");

            if (token.isBlank()) {
                throw new GraphIntegrationException("Brak access token.");
            }

            return token;
        } catch (GraphIntegrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GraphIntegrationException(
                    "Nie udalo sie pobrac tokenu Microsoft Graph.",
                    exception
            );
        }
    }
}