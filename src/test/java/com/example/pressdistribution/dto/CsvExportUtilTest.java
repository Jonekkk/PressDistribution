package com.example.pressdistribution.dto;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportUtilTest {

    // --- escapeCsvField tests ---

    @Test
    void escapeCsvField_normalText_returnsUnchanged() {
        assertThat(CsvExportUtil.escapeCsvField("Hello World")).isEqualTo("Hello World");
    }

    @Test
    void escapeCsvField_withComma_wrapsInDoubleQuotes() {
        assertThat(CsvExportUtil.escapeCsvField("foo,bar")).isEqualTo("\"foo,bar\"");
    }

    @Test
    void escapeCsvField_withDoubleQuote_wrapsAndDoublesQuote() {
        assertThat(CsvExportUtil.escapeCsvField("say \"hello\"")).isEqualTo("\"say \"\"hello\"\"\"");
    }

    @Test
    void escapeCsvField_withCarriageReturn_wrapsInDoubleQuotes() {
        assertThat(CsvExportUtil.escapeCsvField("line1\rline2")).isEqualTo("\"line1\rline2\"");
    }

    @Test
    void escapeCsvField_withLineFeed_wrapsInDoubleQuotes() {
        assertThat(CsvExportUtil.escapeCsvField("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void escapeCsvField_startsWithEquals_prefixesWithApostrophe() {
        assertThat(CsvExportUtil.escapeCsvField("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    }

    @Test
    void escapeCsvField_startsWithPlus_prefixesWithApostrophe() {
        assertThat(CsvExportUtil.escapeCsvField("+cmd")).isEqualTo("'+cmd");
    }

    @Test
    void escapeCsvField_startsWithMinus_prefixesWithApostrophe() {
        assertThat(CsvExportUtil.escapeCsvField("-value")).isEqualTo("'-value");
    }

    @Test
    void escapeCsvField_startsWithAt_prefixesWithApostrophe() {
        assertThat(CsvExportUtil.escapeCsvField("@mention")).isEqualTo("'@mention");
    }

    @Test
    void escapeCsvField_formulaInjectionWithComma_appliesBothProtections() {
        // Field starts with = AND contains comma: should get apostrophe prefix AND double-quote wrapping
        assertThat(CsvExportUtil.escapeCsvField("=cmd,data")).isEqualTo("\"'=cmd,data\"");
    }

    @Test
    void escapeCsvField_null_returnsEmptyString() {
        assertThat(CsvExportUtil.escapeCsvField(null)).isEqualTo("");
    }

    @Test
    void escapeCsvField_emptyString_returnsEmptyString() {
        assertThat(CsvExportUtil.escapeCsvField("")).isEqualTo("");
    }

    // --- writeBom test ---

    @Test
    void writeBom_writesExactlyThreeBomBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExportUtil.writeBom(out);

        byte[] bytes = out.toByteArray();
        assertThat(bytes).hasSize(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    // --- writePublicationReportCsv tests ---

    @Test
    void writePublicationReportCsv_withPaidAmount_includesHeaderWithPaidAmount() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();

        CsvExportUtil.writePublicationReportCsv(List.of(row), true, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        assertThat(lines[0]).isEqualTo(
                "Publication,Issue,Issue date,Parish locality,Parish name,"
                        + "Delivered copies,Returned copies,Sold copies,Unit price,Amount due,Paid amount");
    }

    @Test
    void writePublicationReportCsv_withoutPaidAmount_excludesPaidAmountFromHeader() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();

        CsvExportUtil.writePublicationReportCsv(List.of(row), false, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        assertThat(lines[0]).isEqualTo(
                "Publication,Issue,Issue date,Parish locality,Parish name,"
                        + "Delivered copies,Returned copies,Sold copies,Unit price,Amount due");
    }

    @Test
    void writePublicationReportCsv_setsCorrectResponseHeaders() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsvExportUtil.writePublicationReportCsv(List.of(), true, response);

        assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"publication-report.csv\"");
    }

    @Test
    void writePublicationReportCsv_startsWithBom() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsvExportUtil.writePublicationReportCsv(List.of(), true, response);

        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes.length).isGreaterThanOrEqualTo(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void writePublicationReportCsv_dataRowWithPaidAmount_includesPaidAmountValue() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        row.setPaidAmount(new BigDecimal("15.50"));

        CsvExportUtil.writePublicationReportCsv(List.of(row), true, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Data row is lines[1]
        assertThat(lines[1]).endsWith(",15.50");
    }

    @Test
    void writePublicationReportCsv_dataRowWithoutPaidAmount_excludesPaidAmountValue() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        row.setPaidAmount(new BigDecimal("15.50"));

        CsvExportUtil.writePublicationReportCsv(List.of(row), false, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Data row ends with amountDue, not paidAmount
        assertThat(lines[1]).doesNotContain("15.50");
        assertThat(lines[1]).endsWith(",25.00");
    }

    // --- writeParishReportCsv tests ---

    @Test
    void writeParishReportCsv_includesTotalsRow() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        ReportTotalsDto totals = new ReportTotalsDto(100L, 20L, 80L,
                new BigDecimal("400.00"), new BigDecimal("350.00"));

        CsvExportUtil.writeParishReportCsv(List.of(row), totals, true, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Last line should be the totals row
        String totalsLine = lines[lines.length - 1];
        assertThat(totalsLine).startsWith("Totals");
        assertThat(totalsLine).contains("100");
        assertThat(totalsLine).contains("20");
        assertThat(totalsLine).contains("80");
        assertThat(totalsLine).contains("400.00");
        assertThat(totalsLine).contains("350.00");
    }

    @Test
    void writeParishReportCsv_withoutPaidAmount_excludesPaidAmountFromTotals() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        ReportTotalsDto totals = new ReportTotalsDto(100L, 20L, 80L,
                new BigDecimal("400.00"), new BigDecimal("350.00"));

        CsvExportUtil.writeParishReportCsv(List.of(row), totals, false, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Header should not include "Paid amount"
        assertThat(lines[0]).doesNotContain("Paid amount");

        // Totals row should not include paid amount value
        String totalsLine = lines[lines.length - 1];
        assertThat(totalsLine).doesNotContain("350.00");
        assertThat(totalsLine).contains("400.00");
    }

    @Test
    void writeParishReportCsv_adminFile_namedParishReport() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsvExportUtil.writeParishReportCsv(List.of(), new ReportTotalsDto(), true, response);

        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"parish-report.csv\"");
    }

    @Test
    void writeParishReportCsv_priestFile_namedMyParishReport() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsvExportUtil.writeParishReportCsv(List.of(), new ReportTotalsDto(), false, response);

        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"my-parish-report.csv\"");
    }

    // --- BigDecimal formatting tests ---

    @Test
    void bigDecimalValues_formattedWithTwoDecimalPlaces() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        row.setUnitPrice(new BigDecimal("3.5"));      // 1 decimal place input
        row.setAmountDue(new BigDecimal("17.5"));     // 1 decimal place input
        row.setPaidAmount(new BigDecimal("10"));      // 0 decimal places input

        CsvExportUtil.writePublicationReportCsv(List.of(row), true, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Data row should contain BigDecimal values with exactly 2 decimal places
        assertThat(lines[1]).contains("3.50");
        assertThat(lines[1]).contains("17.50");
        assertThat(lines[1]).contains("10.00");
    }

    @Test
    void bigDecimalNull_formattedAsZeroPointZeroZero() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        row.setUnitPrice(null);
        row.setAmountDue(null);
        row.setPaidAmount(null);

        CsvExportUtil.writePublicationReportCsv(List.of(row), true, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Null BigDecimal values should be "0.00"
        // The row format is: pub,issue,date,locality,name,del,ret,sold,unitPrice,amountDue,paidAmount
        String dataRow = lines[1];
        // Last three numeric fields should be 0.00
        assertThat(dataRow).endsWith(",0.00,0.00,0.00");
    }

    // --- Sold copies calculation in CSV output ---

    @Test
    void soldCopiesCalculation_reflectedCorrectlyInCsvOutput() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReportRowDto row = createSampleReportRow();
        row.setDeliveredCopies(50);
        row.setReturnedCopies(10);
        row.setSoldCopies(40);  // 50 - 10 = 40

        CsvExportUtil.writePublicationReportCsv(List.of(row), true, response);

        String content = getContentAfterBom(response);
        String[] lines = content.split("\r\n");

        // Parse the data row: delivered=50, returned=10, sold=40
        String dataRow = lines[1];
        String[] fields = dataRow.split(",");
        // Fields after parish name (index 5 = delivered, 6 = returned, 7 = sold)
        assertThat(fields[5]).isEqualTo("50");
        assertThat(fields[6]).isEqualTo("10");
        assertThat(fields[7]).isEqualTo("40");
    }

    // --- Helper methods ---

    private ReportRowDto createSampleReportRow() {
        ReportRowDto row = new ReportRowDto();
        row.setPublicationName("Test Publication");
        row.setIssueNumber("2024/01");
        row.setIssueDate(LocalDate.of(2024, 1, 15));
        row.setParishLocality("Valletta");
        row.setParishName("St Paul");
        row.setDeliveredCopies(100);
        row.setReturnedCopies(20);
        row.setSoldCopies(80);
        row.setUnitPrice(new BigDecimal("0.50"));
        row.setAmountDue(new BigDecimal("25.00"));
        row.setPaidAmount(new BigDecimal("20.00"));
        return row;
    }

    private String getContentAfterBom(MockHttpServletResponse response) {
        byte[] bytes = response.getContentAsByteArray();
        // Skip the 3-byte UTF-8 BOM
        return new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
    }
}
