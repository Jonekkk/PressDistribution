package com.example.pressdistribution.dto;

import java.util.List;

public class BulkValidationResult {

    private boolean success;
    private String globalError;
    private int createdCount;
    private int updatedCount;
    private List<BulkRowError> rowErrors;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getGlobalError() {
        return globalError;
    }

    public void setGlobalError(String globalError) {
        this.globalError = globalError;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public List<BulkRowError> getRowErrors() {
        return rowErrors;
    }

    public void setRowErrors(List<BulkRowError> rowErrors) {
        this.rowErrors = rowErrors;
    }
}
