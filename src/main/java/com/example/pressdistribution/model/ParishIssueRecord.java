package com.example.pressdistribution.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "parish_issue_records",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_parish_issue_records_parish_id_issue_id",
                columnNames = {"parish_id", "issue_id"}
        )
)
public class ParishIssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "parish_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_parish_issue_records_parishes_parish_id")
    )
    private Parish parish;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "issue_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_parish_issue_records_issues_issue_id")
    )
    private Issue issue;

    @Column(name = "delivered_copies", nullable = false)
    private Integer deliveredCopies;

    @Column(name = "returned_copies", nullable = false)
    private Integer returnedCopies;

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Parish getParish() {
        return parish;
    }

    public void setParish(Parish parish) {
        this.parish = parish;
    }

    public Issue getIssue() {
        return issue;
    }

    public void setIssue(Issue issue) {
        this.issue = issue;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
