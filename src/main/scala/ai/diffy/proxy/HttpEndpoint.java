package ai.diffy.proxy;

import ai.diffy.functional.endpoints.Endpoint;
import ai.diffy.functional.endpoints.IndependentEndpoint;
import ai.diffy.functional.topology.Async;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServerRequest;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class HttpEndpoint extends IndependentEndpoint<HttpRequest, HttpResponse> {
    private static final Function<HttpServerRequest, CompletableFuture<HttpRequest>> requestBuffer = ((req) ->
            req.receive().aggregate().asString().toFuture().thenApply(body -> new HttpRequest(
                    req.method().name(),
                    req.uri(),
                    req.path(),
                    req.params(),
                    req.requestHeaders(),
                    body
            )));

    public static final Endpoint<HttpServerRequest, CompletableFuture<HttpRequest>> RequestBuffer =
            Async.contain(Endpoint.from("RequestBuffer", () ->requestBuffer));
    public HttpEndpoint(String name, HttpClient client) {
        super(name, () -> (HttpRequest req) ->
            client
                .headers(headers -> headers.add(HttpMessage.toHttpHeaders(req.getHeaders())))
                .request(HttpMethod.valueOf(req.getMethod()))
                .uri(req.getUri())
                .send(ByteBufMono.fromString(Mono.justOrEmpty(req.getBody())))
                .responseSingle(
                    (headers, body) ->
                        body.asString()
                            .map(b -> new HttpResponse(headers.status().toString(), headers.responseHeaders(), b))
                ).block()
        );
    }
    public Endpoint<HttpServerRequest, CompletableFuture<HttpResponse>> withSeverRequestBuffer(){
        return Endpoint.from(this.getName(), () -> (serverRequest -> requestBuffer.apply(serverRequest).thenApply(this::apply)));
    }
    public static HttpEndpoint from(String name, String host, int port) {
        final HttpClient client = HttpClient
                .create().host(host).port(port);
        return new HttpEndpoint(name, client);
    }
}
