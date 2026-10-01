# Nguyện vọng làm việc: chuyển từ chọn tháng sang chọn ngày

**Ngày:** 2026-10-01
**Phạm vi:** trang nhân viên `/employee/preferences`
**Trạng thái:** đã được duyệt để lập kế hoạch triển khai

## Vấn đề

Trang "Đăng ký nguyện vọng" (`frontend/src/pages/employee/EmployeePreferencesPage.tsx`) dùng `<input type="month">` và gợi ý rằng nguyện vọng áp dụng cho cả tháng. Thực tế hệ thống lưu và so khớp **theo từng ngày**:

- Backend lưu `Preference.preferenceDate` kiểu `LocalDate` — một ngày duy nhất (`backend/.../entity/Preference.java:34`). Không tồn tại cột `month`.
- Solver so khớp chính xác: `shift.work_date == preference.preference_date` (`ai-service/app/solver/scheduler.py:274`).
- Giao diện tự cắt tháng thành ngày 1: `` `${form.month}-01` `` (`EmployeePreferencesPage.tsx:139`).

Hai hậu quả:

1. Chọn tháng `2026-10` chỉ ghi `2026-10-01`. Các ngày 2-31 không được tính.
2. Nếu ngày 1 không có ca, `scheduler.py:281` chạy `if not matching: continue` và **bỏ qua nguyện vọng trong im lặng** — không lỗi, không cảnh báo. Người lao động thấy thông báo gửi thành công và tin rằng cả tháng đã được tính.

Không test nào ở backend hoặc ai-service chạy kịch bản tháng-granular này.

## Quyết định

Đổi giao diện sang chọn ngày cụ thể. **Không** đổi mô hình dữ liệu, **không** thêm migration, **không** sửa solver. Mô hình theo ngày đã đúng — vấn đề nằm ở chỗ giao diện nói sai.

Phương án khoảng `start_date`/`end_date` đã được cân nhắc và bị loại vì phạm vi lớn hơn nhu cầu hiện tại.

## Thay đổi

### 2.1 Chọn ngày cụ thể

| Việc | Vị trí |
| --- | --- |
| `input type="month"` → `type="date"`, nhãn "Ngày áp dụng" | `EmployeePreferencesPage.tsx:242-243` |
| Bỏ dòng cắt `` `${form.month}-01` ``, gửi thẳng ngày đã chọn | `EmployeePreferencesPage.tsx:139` |
| Bỏ trường `month` khỏi interface `WorkPreference` | `frontend/src/types/management.ts:123` |
| Bỏ dòng `month: response.preferenceDate.slice(0, 7)` | `frontend/src/adapters/preferenceAdapter.ts:16` |
| Cột "Tháng" → "Ngày", hiển thị ngày đầy đủ | `EmployeePreferencesPage.tsx:208` |
| Dòng "Tháng" trong dialog chi tiết → "Ngày" | `EmployeePreferencesPage.tsx:281` |
| Bộ lọc tháng 2 giá trị hardcode → bộ lọc ngày từ–đết | `EmployeePreferencesPage.tsx:185-189` |
| Giá trị mặc định `'2026-10'` → ngày hiện tại | `EmployeePreferencesPage.tsx:53`, `:160` |
| Khởi tạo lại form sau khi gửi | `EmployeePreferencesPage.tsx:160` |
| Đồng bộ mock seed và mock branch (`slice(0,7)`) | `frontend/src/mocks/managementData.ts`, `frontend/src/services/preferenceService.ts:143,174` |
| Cập nhật test bị ảnh hưởng | `frontend/src/adapters/preferenceAdapter.test.ts`, `frontend/src/services/backendPreferenceService.test.ts` |

Backend nhận `preferenceDate` kiểu ISO ngày đã đúng định dạng, nên không cần đổi request shape.

### 2.2 Gộp 4 lựa chọn loại nguyện vọng còn 2

Dropdown hiện có 4 dòng nhưng chỉ 2 hành vi khác nhau, do `preferenceAdapter.ts:5-10` gộp:

| Chọn | Gửi đi | Khác nhau? |
| --- | --- | --- |
| Muốn làm (`PREFERRED`) | `WANT` | — |
| Ưu tiên ca (`PREFER_SHIFT`) | `WANT` | Không, trùng |
| Không muốn làm (`AVOID`) | `AVOID` | — |
| Tránh ca (`AVOID_SHIFT`) | `AVOID` | Không, trùng |

Bỏ `PREFER_SHIFT` và `AVOID_SHIFT`. Còn lại:

- `Muốn làm` → `WANT` — AI cố gắng xếp ca đó
- `Không muốn làm` → `AVOID` — AI tránh xếp ca đó

`toBackendPreferenceType` rút gọn thành so sánh trực tiếp `AVOID` / `WANT`.

Thuộc tính `relatedShiftCode` **giữ nguyên**. Nó hoạt động đúng qua nhánh `shiftCode`: `resolveShiftTypeId` luôn trả `null` (`EmployeePreferencesPage.tsx:32-39`) nhưng backend phân giải mã ca qua `findByShiftCode` (`EmployeePreferenceService.java:122`). Nguyện vọng "không muốn ca đêm ngày 15" vẫn được hiểu đúng.

### 2.3 Chặn mức ưu tiên 1–5 ở máy chủ

Giao diện giới hạn `min={1} max={5}` (`EmployeePreferencesPage.tsx:262`) nhưng backend chỉ chặn `@Min(1)`, và AI chỉ chặn `ge=1`. Số này nhân thẳng vào hàm mục tiêu tối ưu, nên giá trị vô hạn có thể làm một nguyện vọng áp đảo toàn bộ quyết định xếp ca.

Sửa ở cả ba tầng:

| Tầng | Thay đổi |
| --- | --- |
| Giao diện | Giữ `min`/`max`; thêm kiểm tra khi bấm gửi để chặn sớm với thông báo tiếng Việt |
| Backend | Thêm `@Max(5)` cạnh `@Min(1)` trong `CreatePreferenceRequest` và `UpdatePreferenceRequest` |
| AI service | `priority: int = Field(default=1, ge=1)` → `ge=1, le=5` (`ai-service/app/schemas/scheduling.py:89`) |

Sửa cả ba tầng vì backend là ranh giới thật; chỉ sửa giao diện thì gọi API trực tiếp vẫn lọt.

## Hệ quả và giới hạn đã biết

**Dữ liệu cũ không được sửa tự động.** Các dòng lưu bằng ngày 1 sẽ tiếp tục hiển thị và áp dụng đúng là ngày 1. Không thể suy ra người dùng muốn "cả tháng" hay "ngày 1" vì hai ý đó trùng nhau trong dữ liệu. Bảng `preferences` không có dữ liệu seed và chưa có Flyway migration nào tạo nó, nên ảnh hưởng thực tế gần như bằng không.

**Bộ lọc ngày của trang quản lý bắt đầu hoạt động.** `preferenceService.filterPreferences` hiện truyền cứng `month='ALL', type='ALL'` ở nhánh backend (`preferenceService.ts:49,123`), nên bộ lọc tháng của trang quản lý đang chết. Khi bỏ tham số `month`, bộ lọc ngày sẽ lọc thật — người dùng quen với việc nó không hoạt động có thể thấy bất ngờ.

**Việc chưa làm, nêu rõ để không ai hiểu nhầm là bug:**

- Nguyện vọng `PENDING` vẫn được đưa cho AI. `findActiveInRange` lọc `status IN ('PENDING','APPROVED')` (`PreferenceRepository.java:86`), nên quản lý từ chối mới là cách duy nhất ngăn được. Nguyện vọng chưa ai xét vẫn ảnh hưởng lịch.
- Bảng `preferences` do Hibernate tạo qua `ddl-auto=update` (`application.properties:8`), không có DDL trong Flyway. Vì vậy các ràng buộc `NOT NULL` khai báo ở JPA không chắc đã áp dụng cho bảng có sẵn. Ràng buộc `le=5` ở AI service không phụ thuộc điều này.
- `AiAssignmentPersistenceService` chặn gán ca đè lên nghỉ phép khi ghi kết quả AI, còn việc duyệt nghỉ phép **sau** khi lịch đã tồn tại thì không đụng gì tới lịch. Đây là một đề tài riêng, chưa xử lý.

## Kiểm thử

Frontend (Vitest):
- Adapter ánh xạ `preferenceDate` sang ngày đầy đủ, không còn trường `month`.
- `toBackendPreferenceType` trả `WANT` cho `PREFERRED` và `AVOID` cho `AVOID`, hết nhánh gộp 4 giá trị.
- Form chặn ngày rỗng và chặn mức ưu tiên ngoài 1–5 trước khi gọi service.
- Bộ lọc ngày truyền đúng tham số xuống service.

Backend (JUnit + AssertJ):
- `POST` với `priority = 6` trả 400; `priority = 5` vẫn hợp lệ.
- `PUT` với `priority = 0` và `priority = 6` đều trả 400.
- Các test hiện có vẫn pass với `priority` trong 1–5.

AI service (pytest):
- `priority = 6` bị từ chối bởi schema.
- `priority = 1` và `priority = 5` vẫn nhận.
- Một nguyện vọng `WANT` cho một ngày cụ thể thực sự đổi ca được chọn — bảo đảm điều kiện so khớp theo ngày vẫn hoạt động sau khi bỏ lớp cắt tháng ở giao diện.

## Định nghĩa xong

- Chọn `2026-10-15` thì hệ thống ghi và áp dụng đúng `2026-10-15`, và giao diện hiển thị `2026-10-15` ở cột Ngày, ở bộ lọc, và ở dialog chi tiết.
- Dropdown loại nguyện vọng chỉ còn 2 lựa chọn, mỗi lựa chọn cho một hành vi khác nhau.
- Gửi mức ưu tiên ngoài 1–5 bị từ chối ở cả ba tầng.
- `npm test`, `npm run build`, `npm run lint`, `mvnw test`, `pytest` đều xanh.
