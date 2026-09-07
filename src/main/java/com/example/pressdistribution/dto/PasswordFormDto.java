package com.example.pressdistribution.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordFormDto {

    @NotBlank(message = "Password is required")
    @ValidPassword
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
