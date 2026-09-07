package com.example.pressdistribution.dto;

import java.util.List;

public class BulkEntryFormDto {

    private Long issueId;

    private List<BulkEntryRowDto> rows;

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public List<BulkEntryRowDto> getRows() {
        return rows;
    }

    public void setRows(List<BulkEntryRowDto> rows) {
        this.rows = rows;
    }
}
