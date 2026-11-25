package com.osatum.wbn.web.dashboard.controller;

import com.osatum.wbn.application.dashboard.dto.DashboardCardinalityDto;
import com.osatum.wbn.application.dashboard.dto.DashboardChartsDto;
import com.osatum.wbn.application.dashboard.dto.DashboardNavigationAuditKpiDto;
import com.osatum.wbn.application.dashboard.dto.DashboardSessionDto;
import com.osatum.wbn.application.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dashboard", description = "Dane zbiorcze i statystyki panelu administracyjnego")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/cardinality")
    @Operation(
            summary = "Pobranie liczników dashboardu",
            description = "Zwraca podstawowe liczniki systemowe prezentowane w panelu, w tym liczbę czasopism, instytucji i użytkowników."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono liczniki dashboardu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardCardinalityDto.class)))
    public DashboardCardinalityDto getCardinality() {
        return dashboardService.getCardinality();
    }

    @GetMapping("/charts")
    @Operation(
            summary = "Pobranie danych wykresów dashboardu",
            description = "Zwraca serie danych dla wykresów logowań dziennych oraz logowań instytucji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono dane wykresów dashboardu",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardChartsDto.class)))
    public DashboardChartsDto getCharts() {
        return dashboardService.getCharts();
    }

    @GetMapping("/navigation-audit-kpi")
    @Operation(
            summary = "Pobranie wskaźników audytu nawigacji",
            description = "Zwraca wartości KPI dla zdarzeń audytu nawigacji, używane do monitorowania aktywności w aplikacji."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono wskaźniki audytu nawigacji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardNavigationAuditKpiDto.class)))
    public DashboardNavigationAuditKpiDto getNavigationAuditKpi() {
        return dashboardService.getNavigationAuditKpi();
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "Pobranie ostatnich sesji logowania",
            description = "Zwraca najnowsze sesje logowania użytkowników i instytucji prezentowane w dashboardzie."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono listę ostatnich sesji logowania",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = DashboardSessionDto.class))))
    public List<DashboardSessionDto> getLatestSessions() {
        return dashboardService.getLatestSessions();
    }
}
