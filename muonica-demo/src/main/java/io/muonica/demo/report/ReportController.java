package io.muonica.demo.report;

import io.muonica.core.annotation.api.MuonicaGroup;
import io.muonica.core.annotation.api.MuonicaBadge;
import io.muonica.core.annotation.api.MuonicaOperation;
import io.muonica.core.annotation.api.MuonicaResponse;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.annotation.security.MuonicaSecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@MuonicaGroup(name = "Reports", description = "Build sales summaries and asynchronous export jobs.")
@MuonicaDocumentation(file = "classpath:/muonica/reports/index.md")
class ReportController {
    @GetMapping("/sales")
    @MuonicaOperation(summary = "Read sales report", description = "Aggregates order revenue for a date range and a reporting dimension.")
    @MuonicaDocumentation(file = "classpath:/muonica/reports/sales.md")
    @MuonicaResponse(status = 422, description = "The date range or grouping is invalid", body = ReportErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    SalesReport sales(@RequestParam(name = "from") LocalDate from,
            @RequestParam(name = "to") LocalDate to,
            @RequestParam(name = "groupBy", required = false) Grouping groupBy) {
        return new SalesReport(from, to, new BigDecimal("8420.50"), 128,
                List.of(new DailySales(from, new BigDecimal("8420.50"), 128)));
    }

    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @MuonicaOperation(summary = "Start a report export", description = "Starts an asynchronous export and returns a job that can be polled.")
    @MuonicaDocumentation(file = "classpath:/muonica/reports/start-export.md")
    @MuonicaResponse(status = 422, description = "The export filters are invalid", body = ReportErrorResponse.class)
    @MuonicaSecurityRequirement("apiKey")
    @MuonicaBadge("BETA")
    ExportJob startExport(@Valid @RequestBody CreateExportRequest request) {
        return new ExportJob(UUID.fromString("f7c7d0af-3e7c-4cf2-94c0-32c3b89c55a1"), request.format(), ExportStatus.QUEUED,
                Instant.parse("2026-01-15T09:05:00Z"), "/reports/exports/f7c7d0af-3e7c-4cf2-94c0-32c3b89c55a1");
    }

    @GetMapping("/exports/{id}")
    @MuonicaOperation(summary = "Get export status", description = "Returns the current state and download location of an export job.")
    @MuonicaDocumentation(file = "classpath:/muonica/reports/export-status.md")
    @MuonicaResponse(status = 404, description = "Export job was not found", body = ReportErrorResponse.class)
    @MuonicaSecurityRequirement("apiKey")
    ExportJob exportStatus(@PathVariable UUID id) {
        return new ExportJob(id, ExportFormat.CSV, ExportStatus.READY,
                Instant.parse("2026-01-15T09:05:00Z"), "/reports/exports/" + id);
    }

    @GetMapping(value = "/exports/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @MuonicaOperation(summary = "Download an export", description = "Downloads the completed report as a generated file.")
    @MuonicaDocumentation(file = "classpath:/muonica/reports/download-export.md")
    @MuonicaResponse(status = 404, description = "A ready export was not found", body = ReportErrorResponse.class)
    @MuonicaResponse(status = 409, description = "The export is not ready yet", body = ReportErrorResponse.class)
    @MuonicaSecurityRequirement("apiKey")
    byte[] download(@PathVariable UUID id) {
        return "date,revenue\n2026-01-15,8420.50\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    record SalesReport(LocalDate from, LocalDate to, BigDecimal revenue, long orders, List<DailySales> days) { }

    record DailySales(LocalDate date, BigDecimal revenue, long orders) { }

    record CreateExportRequest(@NotNull LocalDate from, @NotNull LocalDate to, @NotNull ExportFormat format) { }

    record ExportJob(UUID id, ExportFormat format, ExportStatus status, Instant createdAt, String downloadUrl) { }

    record ReportErrorResponse(String code, String message, String field) { }

    enum Grouping { DAY, WEEK, MONTH }

    enum ExportFormat { CSV, JSON }

    enum ExportStatus { QUEUED, PROCESSING, READY, FAILED }
}
