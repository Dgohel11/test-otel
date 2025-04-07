package org.example;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.CustomGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import com.netflix.graphql.dgs.client.RequestExecutor;

import io.prometheus.client.Histogram;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.ThreadContext;
import org.jetbrains.annotations.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
public class FederationService {

    private final ExecutorService federationExecutorService = Executors.newFixedThreadPool(10);
    private final PropertyHolder propertyHolder;
    private final CustomGraphQLClient customGraphQLClient;

    @Autowired
    public FederationService(CustomGraphQLClient customGraphQLClient, PropertyHolder propertyHolder) {
        this.customGraphQLClient = customGraphQLClient;
        this.propertyHolder = propertyHolder;
    }

    public <T> CompletableFuture<Void> executeFederationQuery(GraphQLQueryRequest queryRequest, Class<T> clazz, String query) {
        return CompletableFuture.supplyAsync(
                decorateWithThreadContext(
                        federationQuerySupplier(queryRequest, clazz, query),
                        "getPaymentInstrument"
                ),
                federationExecutorService
        ).thenAccept(result -> {
            // Handle the result
            System.out.println("Result: " + result);
        });
    }

    private <T> Supplier<Optional<T>> federationQuerySupplier(GraphQLQueryRequest graphQLQueryRequest, Class<T> clazz, String query) {
        System.out.println("Printing cuurent otel context: 1 : " + io.opentelemetry.context.Context.current());
        return () -> {
            GraphQLResponse response = customGraphQLClient.executeQuery(graphQLQueryRequest.serialize());
            System.out.println("Printing cuurent otel context: 2 : " + io.opentelemetry.context.Context.current());
            if (response.getData().isEmpty()) {
                throw new RuntimeException("[FederationClient] Empty Data. Request failed for query: " + query);
            }
            return Optional.ofNullable(parseResponse(response, clazz, query));
        };
    }

    private <T> Supplier<T> decorateWithThreadContext(Supplier<T> supplier, String label) {
        Map<String, String> context = ThreadContext.getContext();
        return () -> {
            try (CloseableThreadContext.Instance ignored = CloseableThreadContext.putAll(context)) {
                try (Histogram.Timer timer = Metrics.clientLatency.labels(label).startTimer()) {
                    return supplier.get();
                }
            }
        };
    }

    private <T> T parseResponse(GraphQLResponse response, Class<T> clazz, String query) {
        try (Histogram.Timer ignored = Metrics.responseParserLatency.labels("extractValueAsObject").startTimer()) {
            return response.extractValueAsObject(query, clazz);
        }
    }

    public static class Metrics {
        public static final Histogram clientLatency = Histogram.build()
                .name("service_client_latency")
                .help("Client response time in milliseconds.")
                .labelNames("clientName")
                .register();

        public static final Histogram responseParserLatency = Histogram.build()
                .name("service_response_parser_latency")
                .help("Response Parser time in milliseconds.")
                .labelNames("methodName")
                .register();
    }
}

@Configuration
class GraphQLClientConfig {

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public GraphQLClientConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public CustomGraphQLClient customGraphQLClient(PropertyHolder propertyHolder) {
        return GraphQLClient.createCustom(
                propertyHolder.getFederation().getBaseUrl(),
                new RequestExecutor() {
                    @NotNull
                    @Override
                    public HttpResponse execute(
                            @NotNull String query,
                            @NotNull Map<String, ? extends List<String>> headers,
                            @NotNull String url
                    ) {
                        try {
                            return CompletableFuture.supplyAsync(() -> {
                                try {
                                    String responseString = objectMapper.writeValueAsString(
                                            new SamplePojo("name", 21, "address", "phone", "mail")
                                    );
                                    return new HttpResponse(200, responseString, Map.of()); // 200 OK with empty headers
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException("Failed to serialize response", e);
                                }
                            }, executorService).get();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );
    }

}
