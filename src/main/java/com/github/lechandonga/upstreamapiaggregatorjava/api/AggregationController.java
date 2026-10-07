package com.github.lechandonga.upstreamapiaggregatorjava.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外统一入口：GET /api/aggregate?caller=xxx&q=yyy
 */
@RestController
public class AggregationController {

    private final AggregationService service;

    public AggregationController(AggregationService service) {
        this.service = service;
    }

    @GetMapping("/api/aggregate")
    public ResponseEntity<?> aggregate(@RequestParam String caller,
            @RequestParam(defaultValue = "") String q) {
        try {
            return ResponseEntity.ok(service.aggregate(caller, q));
        } catch (RateLimitedException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage(), null);
        } catch (AggregationException e) {
            return error(HttpStatus.BAD_GATEWAY, "ALL_UPSTREAMS_UNAVAILABLE", e.getMessage(),
                    e.upstreamErrors());
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", e.getMessage(), null);
    }

    private ResponseEntity<ErrorBody> error(HttpStatus status, String code, String message,
            List<String> details) {
        return ResponseEntity.status(status).body(new ErrorBody(code, message, details));
    }

    public record ErrorBody(String code, String message, List<String> details) {
    }
}
