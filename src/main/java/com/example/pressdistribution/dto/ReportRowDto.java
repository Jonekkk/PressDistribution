package com.example.pressdistribution.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReportRowDto {

    private String publicationName;
    private String issueNumber;
    private LocalDate issueDate;
    private String parishLocality;
    private String parishName;
    private Integer deliveredCopies;
    private Integer returnedCopies;
    private Integer soldCopies;
    private BigDecimal unitPrice;
    private BigDecimal amountDue;
    private BigDecimal paidAmount;

    public String getPublicationName() {
        return publicationName;
    }

    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public String getIssueNumber() {
        return issueNumber;
    }

    public void setIssueNumber(String issueNumber) {
        this.issueNumber = issueNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
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

    public Integer getSoldCopies() {
        return soldCopies;
    }

    public void setSoldCopies(Integer soldCopies) {
        this.soldCopies = soldCopies;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(BigDecimal amountDue) {
        this.amountDue = amountDue;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }
}
