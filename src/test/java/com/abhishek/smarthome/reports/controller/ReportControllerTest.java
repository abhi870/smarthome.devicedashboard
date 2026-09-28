package com.abhishek.smarthome.reports.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.dto.output.ReportResponse;
import com.abhishek.smarthome.reports.entity.DailyValue;
import com.abhishek.smarthome.reports.entity.MetricSummary;
import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportType;
import com.abhishek.smarthome.reports.exception.InvalidReportRangeException;
import com.abhishek.smarthome.reports.exception.ReportNotFoundException;
import com.abhishek.smarthome.reports.service.MetricStatistics;
import com.abhishek.smarthome.reports.service.ReportService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ReportController.class)
@Import(GlobalExceptionHandler.class)
class ReportControllerTest {

	private static final UUID HOME_DEVICE_ID = UUID.randomUUID();
	private static final Instant FROM = Instant.parse("2026-09-20T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-09-22T00:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	ReportService reportService;

	private static ReportResponse energyReport() {
		Report report = Report.customRange(HOME_DEVICE_ID, ZoneId.of("UTC"), FROM, TO, Set.of(MetricType.ENERGY), FROM);
		report.start();
		report.complete(
				List.of(MetricSummary.from(new MetricStatistics(MetricType.ENERGY, 2880, 100.0, 103.0, 101.5, 100.0, FROM,
						103.0, TO.minusSeconds(60), 3.0, 172_800, 172_800))),
				List.of(DailyValue.of(MetricType.ENERGY, LocalDate.parse("2026-09-20"), 1.25),
						DailyValue.of(MetricType.ENERGY, LocalDate.parse("2026-09-21"), 1.75)),
				TO);
		return ReportResponse.from(report);
	}

	@Test
	void shouldReturn201WithReport_whenCreated() {
		// given
		given(reportService.createCustomRange(eq(HOME_DEVICE_ID), eq(FROM), eq(TO), any())).willReturn(energyReport());

		// when
		var response = assertThat(mvc.post().uri("/api/v1/smart-home/home-devices/{id}/reports", HOME_DEVICE_ID)
				.contentType(APPLICATION_JSON)
				.content("{\"from\":\"2026-09-20T00:00:00Z\",\"to\":\"2026-09-22T00:00:00Z\",\"metrics\":[\"ENERGY\"]}"));

		// then
		response.hasStatus(HttpStatus.CREATED).doesNotContainHeader("Location");
		response.bodyJson().extractingPath("$.type").isEqualTo("CUSTOM_RANGE");
		response.bodyJson().extractingPath("$.status").isEqualTo("SUCCEEDED");
		response.bodyJson().extractingPath("$.summaries[0].metric").isEqualTo("ENERGY");
		response.bodyJson().extractingPath("$.summaries[0].unit").isEqualTo("kWh");
		response.bodyJson().extractingPath("$.summaries[0].total").isEqualTo(3.0);
		response.bodyJson().extractingPath("$.summaries[0].daily[1].date").isEqualTo("2026-09-21");
		response.bodyJson().extractingPath("$.summaries[0].daily[1].value").isEqualTo(1.75);
		then(reportService).should().createCustomRange(HOME_DEVICE_ID, FROM, TO, Set.of(MetricType.ENERGY));
	}

	@Test
	void shouldReturn400_whenRangeMissing() {
		assertThat(mvc.post().uri("/api/v1/smart-home/home-devices/{id}/reports", HOME_DEVICE_ID)
				.contentType(APPLICATION_JSON).content("{}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors.length()").isEqualTo(2);
		then(reportService).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturn400_whenRangeInvalid() {
		// given
		given(reportService.createCustomRange(eq(HOME_DEVICE_ID), eq(TO), eq(FROM), any()))
				.willThrow(new InvalidReportRangeException("from must be before to"));

		// when / then
		assertThat(mvc.post().uri("/api/v1/smart-home/home-devices/{id}/reports", HOME_DEVICE_ID)
				.contentType(APPLICATION_JSON)
				.content("{\"from\":\"2026-09-22T00:00:00Z\",\"to\":\"2026-09-20T00:00:00Z\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldListReports_withTypeAndIsoDates() {
		// given
		LocalDate from = LocalDate.parse("2026-09-01");
		LocalDate to = LocalDate.parse("2026-09-27");
		given(reportService.list(HOME_DEVICE_ID, ReportType.DAILY, from, to)).willReturn(List.of(energyReport()));

		// when / then
		assertThat(mvc.get().uri("/api/v1/smart-home/home-devices/{id}/reports", HOME_DEVICE_ID).param("type", "DAILY")
				.param("from", "2026-09-01").param("to", "2026-09-27"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.length()").isEqualTo(1);
	}

	@Test
	void shouldReturn404_whenReportUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(reportService.get(id)).willThrow(new ReportNotFoundException(id));

		// when / then
		assertThat(mvc.get().uri("/api/v1/smart-home/reports/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldRegenerateReport() {
		// given
		ReportResponse report = energyReport();
		given(reportService.regenerate(report.getId())).willReturn(report);

		// when / then
		assertThat(mvc.post().uri("/api/v1/smart-home/reports/{id}/regenerate", report.getId()))
				.hasStatusOk()
				.bodyJson().extractingPath("$.id").isEqualTo(report.getId().toString());
	}
}
