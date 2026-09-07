package com.example.pressdistribution.dto;

public class BulkRowError {

    private int rowIndex;
    private String field;
    private String message;

    public BulkRowError() {
    }

    public BulkRowError(int rowIndex, String field, String message) {
        this.rowIndex = rowIndex;
        this.field = field;
        this.message = message;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
