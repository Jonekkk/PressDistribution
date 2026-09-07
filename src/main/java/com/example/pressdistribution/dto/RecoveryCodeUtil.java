package com.example.pressdistribution.dto;

/**
 * Shared utility for formatting and normalizing recovery codes.
 * The canonical form is 16 uppercase characters (no dashes).
 * The display form is XXXX-XXXX-XXXX-XXXX.
 */
public final class RecoveryCodeUtil {

    private RecoveryCodeUtil() {
        // Static utility class
    }

    /**
     * Formats a 16-character canonical recovery code into display format: XXXX-XXXX-XXXX-XXXX.
     *
     * @param rawCode the 16-character canonical code
     * @return formatted code with dashes, or the original value if length is not 16
     */
    public static String formatForDisplay(String rawCode) {
        if (rawCode == null || rawCode.length() != 16) {
            return rawCode;
        }
        return rawCode.substring(0, 4) + "-"
                + rawCode.substring(4, 8) + "-"
                + rawCode.substring(8, 12) + "-"
                + rawCode.substring(12, 16);
    }

    /**
     * Normalizes user input to the canonical 16-character uppercase form.
     * Removes spaces and hyphens, then converts to uppercase.
     *
     * @param input the user-provided recovery code (may contain dashes or spaces)
     * @return canonical uppercase code without separators, or null if input is null
     */
    public static String normalizeInput(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\\s\\-]", "").toUpperCase();
    }
}
