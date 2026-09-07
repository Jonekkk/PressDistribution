package com.example.pressdistribution.dto;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility class for exporting report data to CSV format.
 * Implements RFC 4180 escaping and formula injection protection.
 */
public final class CsvExportUtil {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final String LINE_SEPARATOR = "\r\n";

    private CsvExportUtil() {
        // Static utility class
    }

    /**
     * Writes publication report data as CSV to the HTTP response.
     *
     * @param rows             report data rows
     * @param includePaidAmount true for Administrator (includes Paid amount column), false for Parish Priest
     * @param response         HTTP servlet response
     */
    public static void writePublicationReportCsv(List<ReportRowDto> rows, boolean includePaidAmount,
                                                  HttpServletResponse response) throws IOException {
        setResponseHeaders(response, "publication-report.csv");
        OutputStream out = response.getOutputStream();
        writeBom(out);

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        // Header row
        if (includePaidAmount) {
            writer.print("Publication,Issue,Issue date,Parish locality,Parish name,"
                    + "Delivered copies,Returned copies,Sold copies,Unit price,Amount due,Paid amount");
        } else {
            writer.print("Publication,Issue,Issue date,Parish locality,Parish name,"
                    + "Delivered copies,Returned copies,Sold copies,Unit price,Amount due");
        }
        writer.print(LINE_SEPARATOR);

        // Data rows
        for (ReportRowDto row : rows) {
            writer.print(escapeCsvField(row.getPublicationName()));
            writer.print(',');
            writer.print(escapeCsvField(row.getIssueNumber()));
            writer.print(',');
            writer.print(escapeCsvField(row.getIssueDate() != null ? row.getIssueDate().toString() : ""));
            writer.print(',');
            writer.print(escapeCsvField(row.getParishLocality()));
            writer.print(',');
            writer.print(escapeCsvField(row.getParishName()));
            writer.print(',');
            writer.print(formatInteger(row.getDeliveredCopies()));
            writer.print(',');
            writer.print(formatInteger(row.getReturnedCopies()));
            writer.print(',');
            writer.print(formatInteger(row.getSoldCopies()));
            writer.print(',');
            writer.print(formatDecimal(row.getUnitPrice()));
            writer.print(',');
            writer.print(formatDecimal(row.getAmountDue()));
            if (includePaidAmount) {
                writer.print(',');
                writer.print(formatDecimal(row.getPaidAmount()));
            }
            writer.print(LINE_SEPARATOR);
        }

        writer.flush();
    }

    /**
     * Writes parish report data as CSV to the HTTP response, including a totals row.
     *
     * @param rows             report data rows
     * @param totals           aggregated totals for all filtered rows
     * @param includePaidAmount true for Administrator (includes Paid amount column), false for Parish Priest
     * @param response         HTTP servlet response
     */
    public static void writeParishReportCsv(List<ReportRowDto> rows, ReportTotalsDto totals,
                                             boolean includePaidAmount,
                                             HttpServletResponse response) throws IOException {
        String filename = includePaidAmount ? "parish-report.csv" : "my-parish-report.csv";
        setResponseHeaders(response, filename);
        OutputStream out = response.getOutputStream();
        writeBom(out);

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        // Header row
        if (includePaidAmount) {
            writer.print("Publication,Issue,Issue date,"
                    + "Delivered copies,Returned copies,Sold copies,Unit price,Amount due,Paid amount");
        } else {
            writer.print("Publication,Issue,Issue date,"
                    + "Delivered copies,Returned copies,Sold copies,Unit price,Amount due");
        }
        writer.print(LINE_SEPARATOR);

        // Data rows
        for (ReportRowDto row : rows) {
            writer.print(escapeCsvField(row.getPublicationName()));
            writer.print(',');
            writer.print(escapeCsvField(row.getIssueNumber()));
            writer.print(',');
            writer.print(escapeCsvField(row.getIssueDate() != null ? row.getIssueDate().toString() : ""));
            writer.print(',');
            writer.print(formatInteger(row.getDeliveredCopies()));
            writer.print(',');
            writer.print(formatInteger(row.getReturnedCopies()));
            writer.print(',');
            writer.print(formatInteger(row.getSoldCopies()));
            writer.print(',');
            writer.print(formatDecimal(row.getUnitPrice()));
            writer.print(',');
            writer.print(formatDecimal(row.getAmountDue()));
            if (includePaidAmount) {
                writer.print(',');
                writer.print(formatDecimal(row.getPaidAmount()));
            }
            writer.print(LINE_SEPARATOR);
        }

        // Totals row
        writeTotalsRow(writer, totals, includePaidAmount);

        writer.flush();
    }

    /**
     * Escapes a CSV field value according to RFC 4180 with formula injection protection.
     * <ul>
     *   <li>Fields starting with =, +, -, @ are prefixed with a single apostrophe</li>
     *   <li>Fields containing comma, double quote, CR, or LF are enclosed in double quotes</li>
     *   <li>Internal double-quote characters are escaped by doubling</li>
     * </ul>
     */
    static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }

        String processed = value;

        // Formula injection protection: prefix with apostrophe
        if (!processed.isEmpty()) {
            char first = processed.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                processed = "'" + processed;
            }
        }

        // RFC 4180: check if quoting is needed
        if (needsQuoting(processed)) {
            // Double internal quotes and wrap in double quotes
            processed = "\"" + processed.replace("\"", "\"\"") + "\"";
        }

        return processed;
    }

    /**
     * Writes UTF-8 BOM (EF BB BF) to the output stream.
     */
    static void writeBom(OutputStream out) throws IOException {
        out.write(UTF8_BOM);
    }

    private static void setResponseHeaders(HttpServletResponse response, String filename) {
        response.setContentType(CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");
    }

    private static void writeTotalsRow(PrintWriter writer, ReportTotalsDto totals, boolean includePaidAmount) {
        writer.print(escapeCsvField("Totals"));
        writer.print(',');  // Issue (empty)
        writer.print(',');  // Issue date (empty)
        writer.print(',');
        writer.print(totals.getTotalDelivered() != null ? totals.getTotalDelivered().toString() : "0");
        writer.print(',');
        writer.print(totals.getTotalReturned() != null ? totals.getTotalReturned().toString() : "0");
        writer.print(',');
        writer.print(totals.getTotalSold() != null ? totals.getTotalSold().toString() : "0");
        writer.print(',');  // Unit price (empty in totals)
        writer.print(',');
        writer.print(formatDecimal(totals.getTotalAmountDue()));
        if (includePaidAmount) {
            writer.print(',');
            writer.print(formatDecimal(totals.getTotalPaidAmount()));
        }
        writer.print(LINE_SEPARATOR);
    }

    private static boolean needsQuoting(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\r' || c == '\n') {
                return true;
            }
        }
        return false;
    }

    private static String formatInteger(Integer value) {
        return value != null ? Integer.toString(value) : "0";
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
