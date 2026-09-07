# Press Distribution Management System — Requirements v3

## 1. Purpose and scope

The application manages distribution and settlement of press delivered from a central distributor to parish distribution outlets. It is an alpha prototype with an English user interface.

The application records parishes, users and roles, publications and issues, delivered and returned copies, paid amounts, calculated sales and amount due, audit entries, and reports by publication and parish.

The application does not provide an Excel import screen. Existing Excel data is used only to prepare initial seed data before implementation.

## 2. Roles

| Technical key | UI name | Description |
|---|---|---|
| `ADMINISTRATOR` | Administrator | Full management of users, parishes, publications, issues, parish records, payments and reports. |
| `PARISH_PRIEST` | Parish Priest | User assigned to exactly one parish. Manages delivery and return data only for that parish and can view only its parish report. |

A `PARISH_PRIEST` must have a parish assigned. An `ADMINISTRATOR` must not have a parish assigned.

## 3. Authentication, accounts and recovery codes

### 3.1 Common account functions

- The application shall provide sign-in by email address and password, and sign-out.
- Each user shall be able to edit their own profile data.
- User data shall contain: full name, email address, optional phone number, password hash, recovery code hash, role, active status, creation timestamp and update timestamp.
- `Full name` is one text field and shall allow letters, spaces, dots and other characters used in titles, for example `ks. Jan Kowalski`.
- `Phone number` is optional and shall be stored as text with a maximum length of 20 characters.
- Passwords and recovery codes shall be stored only as secure hashes. The system shall never store or display a previously saved password or recovery code.

### 3.2 Initial administrator

- At application startup, the system shall ensure that the configured initial administrator account exists.
- The initial administrator data shall be read from configuration: full name, email address and password.
- If an account with the configured email already exists, the system shall not create a duplicate and shall not overwrite its password.

### 3.3 Administrator user management

An Administrator shall be able to:

- view a paginated list of users;
- create Administrator and Parish Priest accounts;
- edit other users' data;
- change a user's role;
- assign a Parish Priest to a parish;
- activate or deactivate user accounts;
- set or reset a user's password;
- regenerate a user's recovery code from that user's edit panel.

The Parish Priest creation form shall contain `Full name`, `Email`, optional `Phone number`, generated editable `Password`, `Show password` / `Hide password`, `Locality`, `Parish name / dedication`, and optional `Address`.

A generated password may be displayed only while the Administrator creates or resets the account.

### 3.4 Password reset by Administrator

- An Administrator shall be able to reset another user's password without sending email.
- The system shall generate a password, allow the Administrator to edit it before saving, and display it only during that reset operation.
- A password reset shall automatically invalidate the previous recovery code and generate a new recovery code.
- The new recovery code shall be displayed to the Administrator only during the reset operation so it can be communicated outside the application.

### 3.5 Recovery code

- A recovery code shall be generated when every user account is created.
- The code shall be a cryptographically random, single-use code in the format `XXXX-XXXX-XXXX-XXXX`, using a 16-character Base32 value.
- The clear-text code shall be displayed only immediately after generation or regeneration. It must not be retrievable after the user leaves or refreshes the page.
- Each user shall have a recovery-code section in their own profile/edit panel with a `Generate new recovery code` button.
- An Administrator shall have the same button on another user's edit panel.
- Generating a new recovery code shall invalidate the prior recovery code immediately.
- Every successful password change, including an Administrator password reset and a password reset using a recovery code, shall automatically generate a new recovery code and invalidate the former one.
- The application shall allow a user who has forgotten their password to set a new password using their email address and current recovery code.
- After successful use, the submitted recovery code shall be invalidated and replaced by a new generated code shown to the user once.
- Recovery code verification shall be rate-limited. After five invalid attempts for the same account, further recovery-code attempts shall be blocked temporarily.
- The system shall create an audit log entry when a recovery code is generated, regenerated, used successfully or rejected after rate limiting. The audit log shall not contain the clear-text recovery code.

### 3.6 Last administrator protection

- If the system has exactly one active Administrator, that Administrator shall not be allowed to delete or deactivate their own account.
- The application shall show a clear validation message explaining that at least one active Administrator must remain able to sign in.

### 3.7 Parish Priest restrictions

A Parish Priest shall be able to edit only their own profile, shall not manage users, roles or activation, and shall not access data belonging to another parish.

## 4. Parishes

- A parish is a distribution outlet.
- Parish data shall contain locality, parish name/dedication and optional address.
- The pair `(locality, parish name)` shall be unique.
- An Administrator shall manage all parishes.
- A Parish Priest shall access only their assigned parish.

## 5. Publications and issues

### 5.1 Publications

- A publication shall have only a `Name` field.
- Only an Administrator shall create, edit and delete publications.

### 5.2 Issues

- An issue belongs to exactly one publication.
- An issue shall contain publication, issue number, publication date and unit price.
- The issue number shall be text and allow letters, digits, spaces and slashes, for example `12/2025`, `Easter 2025` or `51-52`.
- The pair `(publication, issue number)` shall be unique.
- When creating an issue, the system shall propose the unit price from the most recently created issue of the selected publication.
- The user may change the proposed price before saving.
- An Administrator and a Parish Priest may create issues. A newly created issue shall be globally visible.
- An Administrator may edit and delete issues.

## 6. Parish issue records and settlement

### 6.1 Data model

For each parish and issue, the system shall store at most one parish issue record. The record shall contain parish, issue, `Delivered copies`, `Returned copies`, `Paid amount`, creation timestamp and update timestamp. These numeric values shall default to zero.

### 6.2 Calculations and validation

\[
\text{Sold copies} = \text{Delivered copies} - \text{Returned copies}
\]

\[
\text{Amount due} = \text{Sold copies} \times \text{Unit price}
\]

The system shall reject negative delivery quantities, return quantities, issue prices and paid amounts. It shall reject a return quantity greater than the delivery quantity. `Sold copies` and `Amount due` are calculated and not independently editable.

### 6.3 Access to operational data

- An Administrator shall create, view and edit every parish issue record, including `Delivered copies`, `Returned copies` and `Paid amount`.
- A Parish Priest shall create, view and edit records only for their assigned parish.
- A Parish Priest may edit `Delivered copies` and `Returned copies`, but shall not view or edit `Paid amount`.

## 7. Bulk entry — optional feature

Bulk entry is an optional Administrator-only feature to be implemented only after the core functions are complete.

- The Administrator selects one issue.
- The system displays all parishes as table rows.
- Each row provides editable fields for `Delivered copies`, `Returned copies` and `Paid amount`.
- Blank fields are unchanged values; a newly created record uses zero for values not entered.
- `Save all` validates all edited rows and saves all changes in one operation.
- If validation fails, no changed row is saved and the system identifies the invalid parish and field.
- The page shows calculated `Sold copies` and `Amount due` for each row.

## 8. Reports and dashboards

### 8.1 Administrator reports

The Administrator shall have two report views.

**Publication report:** sales of selected publication issues across parishes, filterable by date range, issue and parish.

| Publication | Issue | Issue date | Parish | Delivered | Returned | Sold | Unit price | Amount due | Paid amount |
|---|---|---|---|---:|---:|---:|---:|---:|---:|

**Parish report:** all publications and issues for a selected parish in a selected period.

| Publication | Issue | Issue date | Delivered | Returned | Sold | Unit price | Amount due | Paid amount |
|---|---|---|---:|---:|---:|---:|---:|---:|

The parish report shall display totals for delivered copies, returned copies, sold copies, amount due and paid amount.

### 8.2 Parish Priest report

- A Parish Priest shall access only the parish report for their assigned parish.
- A Parish Priest shall not have a parish selector and shall not access the cross-parish publication report.
- `Paid amount` shall not be displayed in the Parish Priest report.

### 8.3 Pagination and CSV export

- Report tables shall use pagination.
- Each report page shall provide an `Export CSV` button.
- CSV export shall include all records matching the selected filters and sorting, not only the visible page.
- CSV export shall include only columns the current role is allowed to view. A Parish Priest export shall not contain `Paid amount`.

## 9. Audit log

- The system shall store audit log entries in `audit_logs`.
- Each entry shall contain `message` and `created_at`.
- The application shall create audit entries for key data-changing operations: account changes, password resets, recovery-code actions, parish changes, publication changes, issue changes and parish issue record changes.

## 10. Initial seed data

- The application shall not provide an Excel import user interface.
- The source Excel file shall be transformed before implementation into seed data, for example JSON or SQL.
- When enabled, the application shall load prepared initial data for parishes, publications, issues and parish issue records at startup.
- Initial data loading shall be controlled by `app.seed.enabled`.
- Seed loading shall be idempotent and shall not create duplicates after repeated application starts.
- The seed mechanism shall be switchable off for environments that should start with an empty database.

## 11. Database schema requirements

The schema shall contain at least `parishes`, `users`, `publications`, `issues`, `parish_issue_records` and `audit_logs`.

The `users` table shall include `recovery_code_hash`. The schema shall enforce the role-to-parish rule through a database `CHECK` constraint and shall enforce one parish issue record per `(parish_id, issue_id)` pair.

## 12. Out of scope

The following are outside the prototype scope unless explicitly added later:

- Excel upload/import from the user interface;
- email or SMS notifications;
- password recovery by email;
- invoices and accounting integrations;
- separate payment transactions or payment history beyond the current `Paid amount` total;
- physical warehouse management;
- route management;
- advanced sales forecasting and charts.
