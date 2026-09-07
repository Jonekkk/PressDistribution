package com.example.pressdistribution.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class CredentialGeneratorService {

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    private static final String RECOVERY_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final int PASSWORD_LENGTH = 16;
    private static final int RECOVERY_CODE_LENGTH = 16;

    private final SecureRandom secureRandom;

    public CredentialGeneratorService() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a random 16-character password guaranteed to satisfy the password policy:
     * at least 1 uppercase, 1 lowercase, 1 digit, 1 special character.
     * Does not use any user-derived data.
     */
    public String generatePassword() {
        char[] password = new char[PASSWORD_LENGTH];

        // Place one guaranteed character from each required category at random positions
        int[] positions = generateUniquePositions(4);
        password[positions[0]] = randomCharFrom(UPPERCASE);
        password[positions[1]] = randomCharFrom(LOWERCASE);
        password[positions[2]] = randomCharFrom(DIGITS);
        password[positions[3]] = randomCharFrom(SPECIAL);

        // Fill remaining positions from the combined pool
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            if (password[i] == 0) {
                password[i] = randomCharFrom(ALL_CHARS);
            }
        }

        // Shuffle to avoid positional bias
        shuffle(password);

        return new String(password);
    }

    /**
     * Generates a 16-character recovery code from the unambiguous alphabet
     * (23456789ABCDEFGHJKLMNPQRSTUVWXYZ). No spaces, no 0/O/1/I/L confusion.
     * Does not use any user-derived data.
     */
    public String generateRecoveryCode() {
        char[] code = new char[RECOVERY_CODE_LENGTH];
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            code[i] = RECOVERY_CODE_ALPHABET.charAt(secureRandom.nextInt(RECOVERY_CODE_ALPHABET.length()));
        }
        return new String(code);
    }

    private char randomCharFrom(String pool) {
        return pool.charAt(secureRandom.nextInt(pool.length()));
    }

    private int[] generateUniquePositions(int count) {
        int[] positions = new int[count];
        for (int i = 0; i < count; i++) {
            int pos;
            boolean unique;
            do {
                pos = secureRandom.nextInt(PASSWORD_LENGTH);
                unique = true;
                for (int j = 0; j < i; j++) {
                    if (positions[j] == pos) {
                        unique = false;
                        break;
                    }
                }
            } while (!unique);
            positions[i] = pos;
        }
        return positions;
    }

    private void shuffle(char[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }
}
