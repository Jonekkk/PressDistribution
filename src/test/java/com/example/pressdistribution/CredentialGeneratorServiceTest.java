package com.example.pressdistribution;

import com.example.pressdistribution.service.CredentialGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialGeneratorServiceTest {

    private static final String RECOVERY_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private CredentialGeneratorService service;

    @BeforeEach
    void setUp() {
        service = new CredentialGeneratorService();
    }

    @Test
    void generatePasswordReturns16Characters() {
        String password = service.generatePassword();
        assertThat(password).hasSize(16);
    }

    @Test
    void generatePasswordContainsUppercase() {
        String password = service.generatePassword();
        assertThat(password).matches(".*[A-Z].*");
    }

    @Test
    void generatePasswordContainsLowercase() {
        String password = service.generatePassword();
        assertThat(password).matches(".*[a-z].*");
    }

    @Test
    void generatePasswordContainsDigit() {
        String password = service.generatePassword();
        assertThat(password).matches(".*[0-9].*");
    }

    @Test
    void generatePasswordContainsSpecialCharacter() {
        String password = service.generatePassword();
        assertThat(password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))).isTrue();
    }

    @Test
    void generatePasswordAlwaysSatisfiesPolicy() {
        // Run multiple times to verify the guarantee holds consistently
        for (int i = 0; i < 100; i++) {
            String password = service.generatePassword();
            assertThat(password).hasSize(16);
            assertThat(password).matches(".*[A-Z].*");
            assertThat(password).matches(".*[a-z].*");
            assertThat(password).matches(".*[0-9].*");
            assertThat(password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)))
                    .as("Password should contain at least one special character: %s", password)
                    .isTrue();
        }
    }

    @Test
    void generatePasswordProducesDifferentResults() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            passwords.add(service.generatePassword());
        }
        // With 16-char passwords from a large pool, collisions are virtually impossible
        assertThat(passwords.size()).isGreaterThan(1);
    }

    @Test
    void generateRecoveryCodeReturns16Characters() {
        String code = service.generateRecoveryCode();
        assertThat(code).hasSize(16);
    }

    @Test
    void generateRecoveryCodeUsesOnlyUnambiguousAlphabet() {
        for (int i = 0; i < 100; i++) {
            String code = service.generateRecoveryCode();
            for (char c : code.toCharArray()) {
                assertThat(RECOVERY_CODE_ALPHABET.indexOf(c))
                        .as("Character '%c' in code '%s' should be in the unambiguous alphabet", c, code)
                        .isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    void generateRecoveryCodeExcludesAmbiguousCharacters() {
        // Generate many codes and verify none contain 0, O, 1, I (excluded from alphabet)
        for (int i = 0; i < 100; i++) {
            String code = service.generateRecoveryCode();
            assertThat(code).doesNotContain("0", "1");
            for (char c : code.toCharArray()) {
                assertThat(c).isNotEqualTo('O');
                assertThat(c).isNotEqualTo('I');
            }
        }
    }

    @Test
    void generateRecoveryCodeContainsNoSpaces() {
        for (int i = 0; i < 100; i++) {
            String code = service.generateRecoveryCode();
            assertThat(code).doesNotContain(" ");
        }
    }

    @Test
    void generateRecoveryCodeProducesDifferentResults() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            codes.add(service.generateRecoveryCode());
        }
        assertThat(codes.size()).isGreaterThan(1);
    }
}
