package com.example.pressdistribution.dto;

public class BulkEntryRowDto {

    private Long parishId;
    private String deliveredCopies;
    private String returnedCopies;
    private String paidAmount;

    public Long getParishId() {
        return parishId;
    }

    public void setParishId(Long parishId) {
        this.parishId = parishId;
    }

    public String getDeliveredCopies() {
        return deliveredCopies;
    }

    public void setDeliveredCopies(String deliveredCopies) {
        this.deliveredCopies = deliveredCopies;
    }

    public String getReturnedCopies() {
        return returnedCopies;
    }

    public void setReturnedCopies(String returnedCopies) {
        this.returnedCopies = returnedCopies;
    }

    public String getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(String paidAmount) {
        this.paidAmount = paidAmount;
    }
}
