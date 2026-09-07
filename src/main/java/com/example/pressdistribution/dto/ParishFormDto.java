package com.example.pressdistribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ParishFormDto {

    @NotBlank(message = "Locality is required")
    @Size(max = 150, message = "Locality must not exceed 150 characters")
    private String locality;

    @NotBlank(message = "Parish name is required")
    @Size(max = 255, message = "Parish name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
