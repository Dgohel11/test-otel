package org.example;

import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FederationController {

    @Autowired
    private FederationService federationService;

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("FederationController");

    private static final ContextKey<SamplePojo> CONTEXT_KEY = ContextKey.named("Context");



    @GetMapping("/executeQuery")
    public String executeQuery() {
        Span span = tracer.spanBuilder("executeQuery").startSpan();
        try {
            GraphQLQuery query = new GraphQLQuery() {
                @NotNull
                @Override
                public String getOperationName() {
                    return "testOperation";
                }
            };
            GraphQLQueryRequest queryRequest = new GraphQLQueryRequest(query, null);

            Context currentContext = Context.current();
            currentContext = currentContext.with(CONTEXT_KEY, new SamplePojo("name", 21, "add", "as", "sa"));
            currentContext.makeCurrent();

            federationService.executeFederationQuery(queryRequest, String.class, "testQuery")
                    .thenAccept(result -> {
                        // Handle the result
                        System.out.println("Result: " + result);
                    })
                    .exceptionally(ex -> {
                        // Handle the exception
                        System.err.println("Error: " + ex.getMessage());
                        return null;
                    });
            return "Query execution started";
        } finally {
            span.end();
        }
    }
}