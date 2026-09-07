package com.example.pressdistribution.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodeUtilTest {

    // --- formatForDisplay ---

    @Test
    void formatForDisplay_16chars_returnsDashedFormat() {
        assertThat(RecoveryCodeUtil.formatForDisplay("ABCDEFGHIJKLMNOP"))
                .isEqualTo("ABCD-EFGH-IJKL-MNOP");
    }

    @Test
    void formatForDisplay_null_returnsNull() {
        assertThat(RecoveryCodeUtil.formatForDisplay(null)).isNull();
    }

    @Test
    void formatForDisplay_shortString_returnsUnchanged() {
        assertThat(RecoveryCodeUtil.formatForDisplay("ABC")).isEqualTo("ABC");
    }

    @Test
    void formatForDisplay_longString_returnsUnchanged() {
        assertThat(RecoveryCodeUtil.formatForDisplay("ABCDEFGHIJKLMNOPQ")).isEqualTo("ABCDEFGHIJKLMNOPQ");
    }

    // --- normalizeInput ---

    @Test
    void normalizeInput_dashedFormat_returnsCanonical() {
        assertThat(RecoveryCodeUtil.normalizeInput("ABCD-EFGH-IJKL-MNOP"))
                .isEqualTo("ABCDEFGHIJKLMNOP");
    }

    @Test
    void normalizeInput_withSpaces_stripsSpaces() {
        assertThat(RecoveryCodeUtil.normalizeInput("ABCD EFGH IJKL MNOP"))
                .isEqualTo("ABCDEFGHIJKLMNOP");
    }

    @Test
    void normalizeInput_lowercase_convertsToUppercase() {
        assertThat(RecoveryCodeUtil.normalizeInput("abcd-efgh-ijkl-mnop"))
                .isEqualTo("ABCDEFGHIJKLMNOP");
    }

    @Test
    void normalizeInput_rawCanonical_returnsUnchanged() {
        assertThat(RecoveryCodeUtil.normalizeInput("ABCDEFGHIJKLMNOP"))
                .isEqualTo("ABCDEFGHIJKLMNOP");
    }

    @Test
    void normalizeInput_null_returnsNull() {
        assertThat(RecoveryCodeUtil.normalizeInput(null)).isNull();
    }

    @Test
    void normalizeInput_mixedSeparators_stripsAll() {
        assertThat(RecoveryCodeUtil.normalizeInput("AB CD-EF GH-IJ KL-MN OP"))
                .isEqualTo("ABCDEFGHIJKLMNOP");
    }
}
