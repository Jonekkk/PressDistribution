-- V1__initial_schema.sql
-- Creates the full 6-table schema for the Press Distribution Management System.
-- Tables are created in dependency order.

-- 1. parishes (no dependencies)
CREATE TABLE parishes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    locality VARCHAR(150) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT UQ_parishes_locality_name UNIQUE (locality, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. publications (no dependencies)
CREATE TABLE publications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT UQ_publications_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. users (FK to parishes)
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone_number VARCHAR(20) NULL,
    password_hash VARCHAR(255) NOT NULL,
    recovery_code_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMINISTRATOR','PARISH_PRIEST') NOT NULL,
    parish_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT FK_users_parishes_parish_id FOREIGN KEY (parish_id) REFERENCES parishes(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT CHK_users_role_parish CHECK ((role = 'PARISH_PRIEST' AND parish_id IS NOT NULL) OR (role = 'ADMINISTRATOR' AND parish_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. issues (FK to publications)
CREATE TABLE issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    issue_number VARCHAR(100) NOT NULL,
    publication_date DATE NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT UQ_issues_publication_id_issue_number UNIQUE (publication_id, issue_number),
    CONSTRAINT FK_issues_publications_publication_id FOREIGN KEY (publication_id) REFERENCES publications(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT CHK_issues_unit_price_positive CHECK (unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. parish_issue_records (FKs to parishes and issues)
CREATE TABLE parish_issue_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parish_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    delivered_copies INT NOT NULL DEFAULT 0,
    returned_copies INT NOT NULL DEFAULT 0,
    paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT UQ_parish_issue_records_parish_id_issue_id UNIQUE (parish_id, issue_id),
    CONSTRAINT FK_parish_issue_records_parishes_parish_id FOREIGN KEY (parish_id) REFERENCES parishes(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK_parish_issue_records_issues_issue_id FOREIGN KEY (issue_id) REFERENCES issues(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT CHK_parish_issue_records_delivered_non_negative CHECK (delivered_copies >= 0),
    CONSTRAINT CHK_parish_issue_records_returned_non_negative CHECK (returned_copies >= 0),
    CONSTRAINT CHK_parish_issue_records_paid_non_negative CHECK (paid_amount >= 0.00),
    CONSTRAINT CHK_parish_issue_records_returned_lte_delivered CHECK (returned_copies <= delivered_copies),
    INDEX IDX_parish_issue_records_issue_id (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. audit_logs (no dependencies)
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
