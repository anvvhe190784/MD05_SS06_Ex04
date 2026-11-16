package com.banking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
public class TransactionApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

record TransferRequest(Long fromAccountId, String toAccountNumber, BigDecimal amount) {}

@RestController
@RequestMapping("/api/transactions")
class TransactionController {
    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);
    private final RestTemplate restTemplate;

    @Value("${account.service.url:http://account-service:8080}")
    private String accountServiceUrl;

    TransactionController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody TransferRequest req) {
        log.info("[transaction-service] Received transfer request: fromAccountId={}, toAccountNumber={}, amount={}",
                req.fromAccountId(), req.toAccountNumber(), req.amount());

        String url = accountServiceUrl + "/api/accounts/" + req.fromAccountId();
        log.info("[transaction-service] Calling account-service via Docker DNS: GET {}", url);

        try {
            ResponseEntity<Map> accountResp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Transfer validated and processed");
            result.put("fromAccount", accountResp.getBody());
            result.put("toAccountNumber", req.toAccountNumber());
            result.put("amount", req.amount());
            log.info("[transaction-service] Source account {} verified. Transfer SUCCESS.", req.fromAccountId());
            return ResponseEntity.ok(result);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[transaction-service] Source account {} not found in account-service.", req.fromAccountId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "FAILED",
                    "message", "Source account not found: " + req.fromAccountId()
            ));
        }
    }
}
