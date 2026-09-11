package com.saas.directory.controller;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.saas.directory.dto.ClientErrorReportDto;

/** Receives the storefront's privacy-filtered production telemetry. */
@RestController
public class ClientErrorController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientErrorController.class);

    @PostMapping("/client-errors")
    public ResponseEntity<Void> report(@Valid @RequestBody ClientErrorReportDto report) {
        LOGGER.warn(
                "Storefront client error: severity=[{}], event=[{}], errorType=[{}], status=[{}], route=[{}]",
                report.severity(),
                report.event(),
                report.errorType(),
                report.status(),
                report.route());
        return ResponseEntity.noContent().build();
    }
}
