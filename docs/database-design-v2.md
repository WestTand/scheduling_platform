# Thiết Kế Cơ Sở Dữ Liệu — Website Phân Công Lịch Làm Việc (v2)

> **Thay đổi so với v1:** Gộp bảng `roles` vào cột `role` trong bảng `accounts` (dùng ENUM/CHECK constraint) vì hệ thống chỉ có 2 vai trò cố định, ít thay đổi. Tổng số bảng giảm từ 14 → 13.

## 1. Mục tiêu thiết kế

- Chuẩn hóa dữ liệu, hạn chế trùng lặp, đảm bảo toàn vẹn tham chiếu giữa các bảng.
- Hỗ trợ đầy đủ 2 nhóm người dùng: **quản lý** và **nhân viên**.
- Lưu được nhiều phiên bản lịch, trạng thái duyệt/công bố và kết quả tối ưu để phục vụ kiểm tra, truy vết.
- Cung cấp dữ liệu đầu vào nhất quán cho dịch vụ tối ưu (Google OR-Tools CP-SAT).
- Đáp ứng quy mô 10–100 nhân viên, dễ mở rộng số loại ca và quy tắc xếp lịch.

## 2. Sơ đồ quan hệ tổng quát (mô tả)

```
accounts (role: MANAGER | EMPLOYEE)
   │ 1-1 (nullable)
   ▼
employees ──< leave_requests
   │        ──< preferences
   │        ──< shift_assignments >── work_shifts ──< schedule_periods
   │        ──< swap_requests (requester/target)
   ▼
shift_assignments ──> shift_types (qua work_shifts)

schedule_periods ──< work_shifts
schedule_periods ──< optimization_runs ──> scheduling_rules
accounts ──< notifications
accounts ──< activity_logs
```

Quan hệ nhiều–nhiều giữa `employees` và `work_shifts` được tách qua bảng trung gian `shift_assignments`.

## 3. Chi tiết các bảng

### 3.1 `accounts` — Tài khoản (đã gộp vai trò)

Lưu thông tin đăng nhập và phân quyền của quản lý và nhân viên. Vai trò được lưu trực tiếp bằng ENUM thay vì bảng `roles` riêng.

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| account_id | BIGSERIAL | PK | |
| employee_id | BIGINT | FK → employees, UNIQUE, NULL | NULL cho phép tài khoản quản lý không gắn hồ sơ nhân viên |
| username | VARCHAR(50) | UNIQUE, NOT NULL | |
| password_hash | VARCHAR(255) | NOT NULL | Mã hóa bằng BCrypt |
| role | account_role (ENUM: `MANAGER`, `EMPLOYEE`) | NOT NULL | Thay thế `role_id` (FK) ở v1 |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| last_login | TIMESTAMP | NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |

```sql
CREATE TYPE account_role AS ENUM ('MANAGER', 'EMPLOYEE');
```

> **Lý do gộp:** Hệ thống chỉ có 2 vai trò cố định, không cần thuộc tính mô tả riêng hay phân quyền chi tiết theo permission — tách bảng `roles` là chuẩn hóa dư thừa cho quy mô đồ án này. Nếu sau này cần RBAC (nhiều vai trò, phân quyền theo chức năng), có thể tách lại `roles` + `permissions` + `role_permissions`.

### 3.2 `employees` — Nhân viên

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| employee_id | BIGSERIAL | PK | |
| employee_code | VARCHAR(20) | UNIQUE, NOT NULL | Mã nhân viên |
| full_name | VARCHAR(100) | NOT NULL | |
| email | VARCHAR(100) | NULL | |
| phone | VARCHAR(20) | NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / ON_LEAVE / INACTIVE |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMP | NULL | |

### 3.3 `shift_types` — Loại ca trực

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| shift_type_id | BIGSERIAL | PK | |
| shift_name | VARCHAR(50) | UNIQUE, NOT NULL | VD: Ngoài giờ, Thông tầm, Trực đêm |
| shift_code | VARCHAR(30) | UNIQUE, NULL (tạm thời) | Mã ổn định (VD: `THONG_TAM`, `NGOAI_GIO_1`, `TRUC_SANG`). Application bắt buộc khi tạo/cập nhật (unique, uppercase snake-case, ≤ 30 ký tự) nhưng DB vẫn NULL-able cho đến migration backfill + `NOT NULL` ở slice sau |
| start_time | TIME | NOT NULL | |
| end_time | TIME | NOT NULL | |
| duration_hours | NUMERIC(4,1) | NOT NULL, CHECK > 0 AND <= 8.0 | |
| is_overnight | BOOLEAN | NOT NULL, DEFAULT FALSE | Ca kéo dài qua ngày hôm sau |
| is_heavy | BOOLEAN | NOT NULL, DEFAULT FALSE | Dùng cho ràng buộc tránh dồn ca |
| min_staff | INT | NOT NULL, CHECK > 0 | |
| max_staff | INT | NOT NULL, CHECK (max_staff >= min_staff) | |
| applicable_weekdays | VARCHAR(40) | NOT NULL | Token ngày ISO, phân tách dấu phẩy, chuẩn hóa theo thứ tự MON..SUN, loại trùng (VD: `MON,TUE,WED,THU,FRI`). Default `MON,TUE,WED,THU,FRI,SAT,SUN` khi tạo mới không truyền |
| applies_on_holidays | BOOLEAN | NOT NULL, DEFAULT FALSE | `true` = ca được phép xếp vào ngày lễ |
| penalty_weight_multiplier | NUMERIC(3,2) | NOT NULL, DEFAULT 1.00, CHECK (0.00–9.99) | Hệ số phạt đã lưu ở backend; AI-service slice hiện tại chưa tiêu thụ |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | |

> Quy tắc applicability: ít nhất 1 weekday bắt buộc trừ khi `applies_on_holidays=true`; token lạ bị từ chối `400 BAD_REQUEST`. Phân loại ngày: `HOLIDAY` (có dòng `holidays` active) > `WEEKEND` (T7/CN) > `WEEKDAY`; `work_shifts` chỉ nhận shift type phù hợp ngày (`Shift type is not applicable on this date` nếu sai).

### 3.4 `schedule_periods` — Kỳ lập lịch

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| period_id | BIGSERIAL | PK | |
| period_name | VARCHAR(100) | NOT NULL | |
| start_date | DATE | NOT NULL | |
| end_date | DATE | NOT NULL, CHECK (end_date >= start_date) | |
| version_no | INT | NOT NULL, DEFAULT 1 | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' | DRAFT / OPTIMIZING / PENDING_APPROVAL / APPROVED / PUBLISHED / CANCELLED |
| created_by | BIGINT | FK → accounts | |
| approved_by | BIGINT | FK → accounts, NULL | |
| published_at | TIMESTAMP | NULL | |
| total_penalty | NUMERIC(10,2) | NULL | Điểm phạt tổng của phương án lịch |

**Unique:** `(start_date, end_date, version_no)`

### 3.5 `work_shifts` — Ca làm việc cụ thể

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| work_shift_id | BIGSERIAL | PK | |
| period_id | BIGINT | FK → schedule_periods, NOT NULL | |
| shift_type_id | BIGINT | FK → shift_types, NOT NULL | |
| work_date | DATE | NOT NULL | Phải nằm trong [start_date, end_date] của period |
| required_staff | INT | NOT NULL, CHECK > 0 | Có thể override min_staff của shift_type |
| note | TEXT | NULL | |

### 3.6 `shift_assignments` — Phân công ca (bảng trung gian n–n)

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| assignment_id | BIGSERIAL | PK | |
| work_shift_id | BIGINT | FK → work_shifts, NOT NULL | |
| employee_id | BIGINT | FK → employees, NOT NULL | |
| assignment_status | VARCHAR(20) | NOT NULL, DEFAULT 'ASSIGNED' | ASSIGNED / CONFIRMED / SWAPPED |
| assigned_source | VARCHAR(20) | NOT NULL, DEFAULT 'AUTO' | AUTO (thuật toán) / MANUAL (quản lý điều chỉnh) |
| is_locked | BOOLEAN | NOT NULL, DEFAULT FALSE | Không bị thuật toán ghi đè khi chạy lại |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |

**Unique:** `(work_shift_id, employee_id)` — chống trùng phân công.

### 3.7 `leave_requests` — Yêu cầu nghỉ

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| leave_request_id | BIGSERIAL | PK | |
| employee_id | BIGINT | FK → employees, NOT NULL | |
| start_date | DATE | NOT NULL | |
| end_date | DATE | NOT NULL, CHECK (end_date >= start_date) | |
| reason | TEXT | NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| reviewed_by | BIGINT | FK → accounts, NULL | |
| reviewed_at | TIMESTAMP | NULL | |

> Đơn nghỉ đã `APPROVED` trở thành ràng buộc cứng khi thuật toán tạo lịch.

### 3.8 `preferences` — Nguyện vọng

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| preference_id | BIGSERIAL | PK | |
| employee_id | BIGINT | FK → employees, NOT NULL | |
| shift_type_id | BIGINT | FK → shift_types, NULL | |
| preference_date | DATE | NOT NULL | |
| preference_type | VARCHAR(20) | NOT NULL | WANT / AVOID |
| priority | INT | NOT NULL, DEFAULT 1 | |
| note | TEXT | NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | |

> Nguyện vọng là ràng buộc mềm (Preference Penalty trong hàm mục tiêu).

### 3.9 `scheduling_rules` — Quy tắc phân lịch

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| rule_id | BIGSERIAL | PK | |
| rule_code | VARCHAR(50) | UNIQUE, NOT NULL | |
| rule_name | VARCHAR(100) | NOT NULL | |
| rule_type | VARCHAR(10) | NOT NULL | HARD / SOFT |
| rule_value_json | JSONB | NOT NULL | Cấu hình chi tiết (VD: định mức ca/tuần) |
| weight | NUMERIC(6,2) | NULL | Trọng số cho ràng buộc mềm |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |

> Ví dụ dữ liệu: định mức "Ngoài giờ: 1 ngày/tuần", "Thông tầm: 2 ngày/tuần", "nghỉ tối thiểu 12h giữa 2 ca", "tối đa 12h/ngày" đều lưu ở đây dưới dạng JSON, không hard-code trong code.

### 3.10 `optimization_runs` — Lần chạy thuật toán

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| run_id | BIGSERIAL | PK | |
| period_id | BIGINT | FK → schedule_periods, NOT NULL | |
| rule_id | BIGINT | FK → scheduling_rules, NULL | |
| started_by | BIGINT | FK → accounts, NULL | |
| status | VARCHAR(20) | NOT NULL | RUNNING / SUCCESS / FAILED / INFEASIBLE |
| weights_json | JSONB | NULL | Bộ trọng số w1..w5 dùng cho lần chạy |
| penalty_details_json | JSONB | NULL | Điểm phạt chi tiết từng tiêu chí |
| total_penalty | NUMERIC(10,2) | NULL | |
| started_at | TIMESTAMP | NOT NULL | |
| finished_at | TIMESTAMP | NULL | |
| error_message | TEXT | NULL | Lý do INFEASIBLE (thiếu nhân sự, định mức không phù hợp...) |

### 3.11 `swap_requests` — Yêu cầu đổi ca

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| swap_request_id | BIGSERIAL | PK | |
| requester_assignment_id | BIGINT | FK → shift_assignments, NOT NULL | Ca của người gửi yêu cầu |
| target_employee_id | BIGINT | FK → employees, NOT NULL | Người được đề nghị nhận/đổi ca |
| target_assignment_id | BIGINT | FK → shift_assignments, NULL | NULL nếu chỉ nhờ nhận ca thay, không đổi qua lại |
| reason | TEXT | NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING_TARGET' | PENDING_TARGET / PENDING_APPROVAL / APPROVED / REJECTED |
| reviewed_by | BIGINT | FK → accounts, NULL | |
| reviewed_at | TIMESTAMP | NULL | |

### 3.12 `notifications` — Thông báo

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| notification_id | BIGSERIAL | PK | |
| recipient_id | BIGINT | FK → accounts, NOT NULL | |
| title | VARCHAR(150) | NOT NULL | |
| content | TEXT | NULL | |
| notification_type | VARCHAR(30) | NOT NULL | VD: SCHEDULE_PUBLISHED, LEAVE_APPROVED, SWAP_REQUESTED |
| is_read | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |

### 3.13 `activity_logs` — Nhật ký hoạt động

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| log_id | BIGSERIAL | PK | |
| account_id | BIGINT | FK → accounts, NOT NULL | |
| action | VARCHAR(30) | NOT NULL | CREATE / UPDATE / DELETE / APPROVE / PUBLISH |
| entity_type | VARCHAR(50) | NOT NULL | |
| entity_id | BIGINT | NOT NULL | |
| old_value_json | JSONB | NULL | |
| new_value_json | JSONB | NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |
| ip_address | VARCHAR(45) | NULL | |

### 3.14 `holidays` — Ngày lễ

| Cột | Kiểu dữ liệu | Ràng buộc | Ghi chú |
|---|---|---|---|
| holiday_id | BIGSERIAL | PK | |
| holiday_date | DATE | UNIQUE, NOT NULL | Ngày lễ Gregorian/Việt Nam (`YYYY-MM-DD`); upsert theo cột này |
| holiday_name | VARCHAR(150) | NOT NULL | Tên hiển thị |
| holiday_year | INT | NOT NULL | Năm của ngày lễ |
| holiday_type | VARCHAR(30) | NOT NULL | `PUBLIC_HOLIDAY` / `LUNAR_HOLIDAY` / `SUBSTITUTE_HOLIDAY` / `SPECIAL_HOLIDAY` |
| source_type | VARCHAR(30) | NOT NULL | `FIXED_RULE` (lễ cố định 01/01, 30/04, 01/05, 02/09) / `ANNUAL_SEED` (import JSON năm) |
| source_reference | VARCHAR(255) | NULL | Tham chiếu nguồn (VD: thông báo lịch nghỉ năm) |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | Chỉ dòng active tham gia phân loại ngày |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMP | NULL | |

> Nguồn dữ liệu: backend luôn seed lễ cố định + import `backend/src/main/resources/holidays/holidays-YYYY.json` (idempotent, trùng ngày thì update). Không lưu chuỗi âm lịch, không convert âm–dương trong code. Chưa có CRUD quản lý nhập lễ và chưa có bảng trạng thái đầy đủ lịch lễ trong slice này.

## 4. Bảng tổng hợp

| Bảng | Mục đích |
|---|---|
| accounts | Đăng nhập & phân quyền (role gộp trực tiếp) |
| employees | Hồ sơ nhân viên |
| shift_types | Loại ca & định mức nhân sự tối thiểu/tối đa |
| schedule_periods | Kỳ lập lịch, phiên bản, trạng thái duyệt/công bố |
| work_shifts | Ca cụ thể theo ngày |
| shift_assignments | Kết quả phân công (bảng trung gian n–n) |
| leave_requests | Đăng ký & duyệt nghỉ |
| preferences | Nguyện vọng làm/không làm |
| scheduling_rules | Cấu hình ràng buộc cứng/mềm cho thuật toán |
| optimization_runs | Lịch sử chạy CP-SAT, điểm phạt, lỗi INFEASIBLE |
| swap_requests | Yêu cầu đổi/nhận ca |
| notifications | Thông báo hệ thống |
| activity_logs | Nhật ký thao tác, phục vụ truy vết |
| holidays | Ngày lễ (seed cố định + JSON năm), dùng phân loại ngày HOLIDAY/WEEKEND/WEEKDAY |

## 5. Ràng buộc toàn vẹn & quy tắc dữ liệu

| Nhóm | Quy tắc |
|---|---|
| Khóa & duy nhất | Mọi bảng có PK; `employee_code`, `username`, `rule_code` là duy nhất. `shift_types.shift_code` unique (DB-nullable tạm thời, application bắt buộc). `holidays.holiday_date` unique. `(work_shift_id, employee_id)` trong `shift_assignments` không được trùng. |
| Ngày & thời gian | `start_date ≤ end_date`; `duration_hours > 0`; `required_staff > 0`; `min_staff ≤ max_staff`; ngày của `work_shifts` phải nằm trong khoảng của `schedule_periods`. |
| Nghiệp vụ xếp lịch | Một nhân viên không được làm 2 ca trùng thời gian, tối đa 12h/ngày, nghỉ tối thiểu 12h giữa 2 ca — kiểm tra ở tầng service/thuật toán, không lưu cột riêng. |
| Trạng thái | Dùng tập giá trị kiểm soát (CHECK/ENUM) cho `accounts.role`, đơn nghỉ, nguyện vọng, lịch, phân công, đổi ca. |
| Xóa dữ liệu | Ưu tiên khóa/ngừng hoạt động (`status='INACTIVE'`) thay vì xóa cứng nhân viên, tài khoản, loại ca. Lịch đã `PUBLISHED` không được xóa tùy ý. |
| Bảo mật | Chỉ lưu `password_hash` (BCrypt); phân quyền theo `accounts.role`; mọi thay đổi quan trọng ghi vào `activity_logs`. |

## 6. Chỉ mục & tối ưu truy vấn

- `employees(employee_code)`, `accounts(username)`
- `work_shifts(work_date, shift_type_id)`
- `shift_assignments(employee_id, work_shift_id)`
- `leave_requests(employee_id, start_date, end_date)`
- `preferences(employee_id, preference_date)`
- `notifications(recipient_id, is_read, created_at)`
- `holidays(holiday_date)` (unique + index `idx_holidays_date`)
- Unique: `schedule_periods(start_date, end_date, version_no)`, `shift_assignments(work_shift_id, employee_id)`
- Dùng kiểu `JSONB` cho `weights_json`, `rule_value_json`, `penalty_details_json`, `old_value_json`/`new_value_json` — chỉ dùng khi cấu trúc cần linh hoạt, không thay thế cột quan hệ cốt lõi.

## 7. Ghi chú thiết kế mở (cần xác nhận thêm)

- **Định mức ca cá nhân hóa:** nếu định mức (1 ngoài giờ/tuần, 2 thông tầm/tuần...) cần khác nhau theo từng nhân viên thay vì áp dụng chung, cần bổ sung bảng `employee_shift_quota (employee_id, shift_type_id, max_per_week)`.
- **Đa phòng ban:** thiết kế hiện tại là single-tenant (1 phòng/lần triển khai). Nếu cần nhiều phòng ban dùng chung hệ thống, cần bổ sung bảng `departments` và khóa ngoại `department_id` trong `employees`/`schedule_periods`.
