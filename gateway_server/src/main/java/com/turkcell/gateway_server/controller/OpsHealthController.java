package com.turkcell.gateway_server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Operasyon konsolu icin GERCEK saglik aggregatoru.
 * Tarayici, gateway'den acilan sayfadan dogrudan 9002-9010 portlarina istek
 * atarsa CORS'a takilir (o servislerde CORS yok). Bu endpoint sunucu tarafinda
 * her servisin /actuator/health'ini yoklar ve tek JSON doner — arayuz tek,
 * ayni-origin cagriyla tum servislerin gercek durumunu alir.
 *
 * Guvenlik: /ops/** SecurityConfig'de permitAll (izleme amacli, veri sizdirmaz).
 */
@RestController
@RequestMapping("/ops")
public class OpsHealthController {

    private record Svc(String name, String url) {}

    // Actuator health'i olan servisler (infra bilesenleri farkli health yoluna sahip,
    // arayuz onlari zaten "altyapi" olarak gosterir).
    private static final List<Svc> SERVICES = List.of(
            new Svc("gateway-server", "http://localhost:8080/actuator/health"),
            new Svc("eureka-server", "http://localhost:8761/actuator/health"),
            new Svc("config-server", "http://localhost:8888/actuator/health"),
            new Svc("customer-service", "http://localhost:9002/actuator/health"),
            new Svc("product-catalog-service", "http://localhost:9003/actuator/health"),
            new Svc("order-service", "http://localhost:9004/actuator/health"),
            new Svc("subscription-service", "http://localhost:9005/actuator/health"),
            new Svc("usage-service", "http://localhost:9006/actuator/health"),
            new Svc("billing-service", "http://localhost:9007/actuator/health"),
            new Svc("payment-service", "http://localhost:9008/actuator/health"),
            new Svc("notification-service", "http://localhost:9009/actuator/health"),
            new Svc("ticket-service", "http://localhost:9010/actuator/health")
    );

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    @GetMapping("/health")
    public Map<String, String> aggregate() {
        List<CompletableFuture<Map.Entry<String, String>>> futures = SERVICES.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> Map.entry(s.name(), probe(s.url()))))
                .toList();

        Map<String, String> out = new LinkedHashMap<>();
        for (var f : futures) {
            try {
                var e = f.get();
                out.put(e.getKey(), e.getValue());
            } catch (Exception ex) {
                // yoklama hata verirse bilinmiyor say
            }
        }
        return out;
    }

    private String probe(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
