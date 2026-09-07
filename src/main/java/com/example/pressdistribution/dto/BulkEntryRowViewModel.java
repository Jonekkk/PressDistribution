package com.example.pressdistribution.dto;

import java.math.BigDecimal;

public class BulkEntryRowViewModel {

    private Long parishId;
    private String parishLocality;
    private String parishName;
    private Integer deliveredCopies;
    private Integer returnedCopies;
    private BigDecimal paidAmount;
    private Integer soldCopies;
    private BigDecimal amountDue;
    private boolean existingRecord;
    private String deliveredError;
    private String returnedError;
    private String paidAmountError;

    public Long getParishId() {
        return parishId;
    }

    public void setParishId(Long parishId) {
        this.parishId = parishId;
    }

    public String getParishLocality() {
        return parishLocality;
    }

    public void setParishLocality(String parishLocality) {
        this.parishLocality = parishLocality;
    }

    public String getParishName() {
        return parishName;
    }

    public void setParishName(String parishName) {
        this.parishName = parishName;
    }

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

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public Integer getSoldCopies() {
        return soldCopies;
    }

    public void setSoldCopies(Integer soldCopies) {
        this.soldCopies = soldCopies;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(BigDecimal amountDue) {
        this.amountDue = amountDue;
    }

    public boolean isExistingRecord() {
        return existingRecord;
    }

    public void setExistingRecord(boolean existingRecord) {
        this.existingRecord = existingRecord;
    }

    public String getDeliveredError() {
        return deliveredError;
    }

    public void setDeliveredError(String deliveredError) {
        this.deliveredError = deliveredError;
    }

    public String getReturnedError() {
        return returnedError;
    }

    public void setReturnedError(String returnedError) {
        this.returnedError = returnedError;
    }

    public String getPaidAmountError() {
        return paidAmountError;
    }

    public void setPaidAmountError(String paidAmountError) {
        this.paidAmountError = paidAmountError;
    }
}
