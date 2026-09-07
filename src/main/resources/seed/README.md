# Seed Data

## Overview

This directory contains static JSON seed files that populate the database with a fictional sample dataset for local development. The seed loader activates only when `app.seed.enabled=true`.

## Seed Files

| File | Description |
|------|-------------|
| `parishes.json` | Populates the `parishes` table with distribution outlet records. |
| `publications.json` | Populates the `publications` table with press title records. |
| `issues.json` | Populates the `issues` table with edition records linked to publications. |
| `parish-issue-records.json` | Populates the `parish_issue_records` table with delivery, return, and payment data per parish per issue. |

## Field Contracts

### parishes.json

| Field | Type | Required | Max Length | Format / Notes |
|-------|------|----------|------------|----------------|
| `locality` | string | Yes | 150 characters | Non-empty |
| `name` | string | Yes | 255 characters | Non-empty |
| `address` | string | No | 500 characters | Optional physical address |

### publications.json

| Field | Type | Required | Max Length | Format / Notes |
|-------|------|----------|------------|----------------|
| `name` | string | Yes | 255 characters | Non-empty, unique across all records |

### issues.json

| Field | Type | Required | Max Length / Precision | Format / Notes |
|-------|------|----------|------------------------|----------------|
| `publicationName` | string | Yes | 255 characters | Must match an existing publication name |
| `issueNumber` | string | Yes | 100 characters | Letters, digits, spaces, and slashes only |
| `publicationDate` | date | Yes | — | ISO 8601 format: `YYYY-MM-DD` |
| `unitPrice` | decimal | Yes | DECIMAL(10,2), range 0.00–99999999.99 | Non-negative, at most 2 decimal places |

### parish-issue-records.json

| Field | Type | Required | Max Length / Precision | Format / Notes |
|-------|------|----------|------------------------|----------------|
| `parishLocality` | string | Yes | 150 characters | Must match an existing parish locality |
| `parishName` | string | Yes | 255 characters | Must match an existing parish name |
| `publicationName` | string | Yes | 255 characters | Must match an existing publication name |
| `issueNumber` | string | Yes | 100 characters | Must match an existing issue number for the given publication |
| `deliveredCopies` | integer | Yes | INT (4 bytes) | Non-negative integer |
| `returnedCopies` | integer | Yes | INT (4 bytes) | Non-negative integer, must not exceed `deliveredCopies` |
| `paidAmount` | decimal | Yes | DECIMAL(10,2), range 0.00–99999999.99 | Non-negative, at most 2 decimal places |

## Cross-Entity References (Natural Keys)

Seed files use natural business keys instead of database-generated IDs to reference related entities:

- **Issue → Publication**: Each issue record references its parent publication by the `publicationName` field, which must match the `name` field of a record in `publications.json`.
- **ParishIssueRecord → Parish**: Each parish issue record references its parish by the combination of `parishLocality` and `parishName`, which must match the `locality` and `name` fields of a record in `parishes.json`.
- **ParishIssueRecord → Issue**: Each parish issue record references its issue by the combination of `publicationName` and `issueNumber`, which must match a record in `issues.json`.

## Required Load Order

The seed loader processes files in the following fixed order:

1. **parishes** — no dependencies
2. **publications** — no dependencies
3. **issues** — depends on publications
4. **parish issue records** — depends on parishes and issues

Referenced entities must exist before dependent records are loaded. The loader enforces this order internally; you do not need to run it multiple times.

## Validation Rules

### Uniqueness Constraints

- Parishes: each `(locality, name)` pair must be unique within the file.
- Publications: each `name` must be unique within the file.
- Issues: each `(publicationName, issueNumber)` pair must be unique within the file.
- Parish issue records: each `(parishLocality + parishName, publicationName + issueNumber)` combination must be unique within the file.

### Numeric Value Rules

- `deliveredCopies` and `returnedCopies` must be non-negative integers.
- `returnedCopies` must not exceed `deliveredCopies`.
- `unitPrice` and `paidAmount` must be non-negative with at most 2 decimal places.

### String Value Rules

- All required string fields must be non-empty.
- String lengths must not exceed the maximums documented in the field contracts above.
- `issueNumber` may contain only letters, digits, spaces, and slashes.

### Date Rules

- `publicationDate` must be a valid date in ISO 8601 format (`YYYY-MM-DD`).

## Replacing the Dataset

The seed loader **never updates, replaces, or deletes existing records**. It only inserts records that do not yet exist (matched by natural key). To replace the fictional dataset with real data, follow these steps:

1. **Prepare a clean database.** Either:
   - Use a new empty development database (drop and recreate), or
   - Explicitly remove the fictional seed data from the existing database (delete rows from `parish_issue_records`, `issues`, `publications`, and `parishes` in reverse dependency order).

2. **Prepare new JSON files.** Convert your Excel source data into JSON files matching the format documented above. Ensure:
   - Each file is a JSON array of objects.
   - All required fields are present with correct types.
   - Natural key references between files are consistent.

3. **Verify validation rules.** Before loading, confirm that:
   - All uniqueness constraints are satisfied within each file.
   - Numeric values are non-negative and within range.
   - `returnedCopies` does not exceed `deliveredCopies` in any record.
   - Monetary values have at most 2 decimal places.
   - All cross-entity references resolve to records in the corresponding file.

4. **Load the data.** Run the application with `app.seed.enabled=true`:
   ```bash
   APP_SEED_ENABLED=true ./mvnw spring-boot:run
   ```

5. **Verify the result.** Check the application logs for successful seed loading. The loader logs an info message upon completion.

> **Important:** The seed loader is additive only. It will skip any record whose natural key already exists in the database. If you need to correct previously loaded seed data, you must manually update or delete the existing records first.
