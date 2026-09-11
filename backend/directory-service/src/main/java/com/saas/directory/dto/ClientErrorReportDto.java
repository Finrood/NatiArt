package com.saas.directory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientErrorReportDto(
        @NotBlank @Pattern(regexp = "error|warning") String severity,

        @NotBlank @Size(max = 32) @Pattern(regexp = "[a-z-]+")
        String event,

        @Size(max = 32) @Pattern(regexp = "[A-Za-z]+") String errorType,
        @Min(100) @Max(599) Integer status,

        @Size(max = 128) @Pattern(regexp = "[/a-zA-Z0-9._:-]*")
        String route) {}
