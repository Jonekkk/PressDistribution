package com.example.pressdistribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PublicationFormDto {

    @NotBlank(message = "Publication name is required")
    @Size(max = 255, message = "Publication name must not exceed 255 characters")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
