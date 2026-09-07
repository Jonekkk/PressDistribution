package com.example.pressdistribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PriestRecordEditFormDto {

    @NotNull(message = "Delivered copies is required")
    @Min(value = 0, message = "Delivered copies must be at least 0")
    @Max(value = 2147483647, message = "Delivered copies exceeds maximum")
    private Integer deliveredCopies;

    @NotNull(message = "Returned copies is required")
    @Min(value = 0, message = "Returned copies must be at least 0")
    @Max(value = 2147483647, message = "Returned copies exceeds maximum")
    private Integer returnedCopies;

    public Integer getDeliveredCopies() {
        return deliveredCopies;
    }

    public void setDeliveredCopies(Integer deliveredCopies) {
        this.deliveredCopies = deliveredCopies;
    }

    public Integer getReturnedCopies() {
        return returnedCopies;
    }

    public void setReturnedCopies(Integer returnedCopies) {
        this.returnedCopies = returnedCopies;
    }
}
