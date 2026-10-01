# Duyệt nghỉ phép và lịch đã xếp — Implementation Plan

> **Trạng thái:** THIẾT KẾ ĐÃ THỐNG NHẤT, CHƯA TRIỂN KHAI. Tài liệu này chỉ ghi lại quyết định nghiệp vụ và kiến trúc. Chưa có code nào được viết cho đề tài này.

> **For agentic workers:** khi được yêu cầu triển khai, dùng superpowers:subagent-driven-development hoặc superpowers:executing-plans, thực hiện từng task và dừng lại review sau mỗi task.

**Mục tiêu:** khi quản lý duyệt một nghỉ phép đã có phân công trong lịch, hệ thống phải phản ánh điều đó vào lịch thay vì bỏ qua.

**Kiến trúc:** một service dùng chung `LeaveScheduleImpactService` là nguồn sự thật duy nhất cho câu hỏi "nghỉ phép này ảnh hưởng những ca nào". Cả duyệt nghỉ phép và công bố lịch đều gọi nó.

**Công nghệ:** Java 17 / Spring Boot / JPA, React + TypeScript, Vitest, JUnit 5 + AssertJ + Mockito.

---

## Vấn đề hiện tại

Nghỉ phép **có** ảnh hưởng tới lịch, nhưng chỉ đúng ở một thời điểm duy nhất.

Khi AI tối ưu, backend gửi `approvedLeaves` cho solver (`AiSchedulingPayloadBuilder.java:124`), và solver cấm hẳn mọi ca chồng ngày nghỉ (`ai-service/app/solver/model_builder.py:53-58`). Nếu nghỉ làm thiếu người, kết quả là `INFEASIBLE` kèm chẩn đoán `INSUFFICIENT_AVAILABLE_STAFF`.

Nhưng `AdminLeaveRequestService.approveLeaveRequest` (`:69-84`) **không đụng gì tới lịch**. Nó chỉ đổi trạng thái, ghi log, gửi thông báo. Service này không có dependency nào tới `ShiftAssignment`.

Hệ quả là kịch bản này hiện xảy ra không bị chặn:

1. Quản lý duyệt lịch, nhân viên có ca, lịch công bố xong.
2. Nhân viên nộp đơn nghỉ phép, quản lý duyệt.
3. Ca của nhân viên vẫn nằm trong `shift_assignments` đè lên ngày nghỉ.
4. Không có gỡ assignment, không kích hoạt chạy lại AI, không gắn cờ nào.
5. Công bố lịch vẫn thành công vì `approveSchedulePeriod` không hề kiểm tra nghỉ phép.

`validateEmployeeAvailability` của `AdminShiftAssignmentService` chỉ chạy khi quản lý tự thêm/sửa ca, nên không bắt được các dòng dữ liệu cũ này. Nhân viên cũng không được báo là mình có nghỉ.

Cả hai bộ test đều **không có test nào** cho kịch bản duyệt nghỉ phép sau khi lịch đã tồn tại.

## Bất biến của hệ thống

`EmployeeScheduleService.getOwnSchedule` chỉ trả về ca thuộc kỳ lịch `PUBLISHED` (`EmployeeScheduleService.java:40-41`).

Suy ra: **nhân viên không bao giờ nhìn thấy lịch `DRAFT` hoặc `APPROVED`.** Mọi sửa đổi trên lịch ở hai trạng thái đó là vô hình với nhân viên, nên ta sửa tự do mà không cần thông báo ai.

## Ràng buộc đã phát hiện khi thiết kế

`SchedulePeriod` chỉ có ba chuyển trạng thái: sang `APPROVED` (duyệt), `PUBLISHED` (công bố), `CANCELLED` (huỷ). `cancelSchedulePeriod` chỉ cho phép huỷ khi đang `DRAFT` hoặc `APPROVED` (`AdminSchedulePeriodService.java:196`).

**Không có đường nào đưa `PUBLISHED` về lại `DRAFT`.** Đây là ràng buộc quyết định toàn bộ thiết kế, và nó là lý do quy tắc R3 tồn tại.

---

## Quy tắc nghiệp vụ

| # | Tình huống | Hành vi |
| --- | --- | --- |
| **R1** | Duyệt nghỉ phép `APPROVED`, các ca chồng nằm trong kỳ lịch `DRAFT` hoặc `APPROVED` | Gỡ assignment của nhân viên khỏi những ca đó. Ca trở nên thiếu người. Trả về danh sách ca bị ảnh hưởng cho giao diện hiển thị. |
| **R2** | Duyệt nghỉ phép `APPROVED`, có ít nhất một kỳ lịch `PUBLISHED` chồng ngày | Chặn, trả 409 kèm thông báo tiếng Việt giải thích lý do và cần làm gì tiếp. Không gỡ gì. |
| **R3** | Công bố một kỳ lịch mà còn nghỉ phép `PENDING` chồng lên kỳ lịch đó | Chặn công bố, trả 409 kèm danh sách nghỉ phép cần xử lý trước. |
| **R4** | Quản lý mở duyệt một nghỉ phép `PENDING` đè lên ca đã phân công | Hiển thị cảnh báo liệt kê các ca sẽ bị ảnh hưởng, trước khi bấm duyệt. Chỉ cảnh báo, chưa gỡ gì. |

Nghỉ phép `PENDING` không bao giờ thay đổi lịch. Chỉ hành động duyệt mới gỡ assignment, và việc gỡ chỉ xảy ra ở R1.

### R3 là mốc chốt

R3 biến `PUBLISHED` thành ranh giới: quản lý buộc phải dọn hết nghỉ phép tồn đọng trước khi chốt lịch. Quy tắc nghiệp vụ ngầm là **nghỉ phép phải được đăng ký và xử lý trước khi lịch công bố**.

### Giới hạn cần nói thẳng

Sau khi lịch đã `PUBLISHED`, một đơn nghỉ nộp muộn sẽ **không được duyệt** (R2). Vì không có đường thu hồi `PUBLISHED`, đây là ranh giới nghiệp vụ có chủ đích, không phải lỗi. Trường hợp khẩn cấp phải xử lý ngoài hệ thống.

Đây là hệ quả trực tiếp của việc không có chức năng thu hồi lịch đã công bố. Nếu sau này nghiệp vụ cần hỗ trợ nghỉ khẩn cấp trong lịch đã công bố, phải bổ sung chức năng thu hồi lịch trước, và đó là một đề tài riêng.

---

## Thiết kế chi tiết

### `LeaveScheduleImpactService`

Service mới, đặt trong `backend/src/main/java/scheduling/scheduling/admin/`. Trách nhiệm duy nhất: xác định ảnh hưởng của một khoảng nghỉ phép lên các ca đã phân công.

Đầu vào: `employeeId`, `startDate`, `endDate`.
Đầu ra: danh sách ca bị ảnh hưởng, mỗi ca gồm `workShiftId`, ngày, tên ca, `requiredStaff`, và số người đang được xếp.

Phương pháp cần có:

- `findAffectedShifts(employeeId, startDate, endDate)` — truy vấn các assignment của nhân viên có `workShift.workDate` nằm trong khoảng ngày nghỉ. Chỉ đọc, không ghi.
- `findPublishedPeriodsOverlapping(startDate, endDate)` — phục vụ R2.
- `removeAssignments(List<AffectedShift>)` — xóa các `ShiftAssignment` của nhân viên khỏi những ca đó, trả về danh sách đã gỡ.
- `hasPendingLeaveOverlapping(periodStart, periodEnd)` — phục vụ R3.

Cần một truy vấn repository mới trong `ShiftAssignmentRepository` để tìm assignment theo khoảng ngày, vì `listForPeriod` chỉ lọc theo kỳ lịch.

### Luồng duyệt nghỉ phép

`AdminLeaveRequestService.approveLeaveRequest` chèn hai bước mới, đặt SAU `requirePending` và kiểm tra trùng, TRƯỚC `applyReview`:

1. Kiểm tra R2. Nếu có kỳ lịch `PUBLISHED` chồng ngày thì ném 409 với message tiếng Việt nói rõ lịch đã công bố và cần xử lý lịch trước. **Việc này phải chạy trước R1** để tránh gỡ assignment rồi mới phát hiện là bị chặn.
2. Nếu không bị chặn, gọi `findAffectedShifts`. Với mỗi ca trả về, gọi `removeAssignments` cho các ca nằm trong kỳ lịch `DRAFT`/`APPROVED`.

Ghi log activity cho mỗi assignment bị gỡ, theo đúng convention của `AdminShiftAssignmentService` (ghi cả trước và sau).

Sau khi gỡ, các ca đó thiếu người. `validateStaffing` hiện có của `AdminSchedulePeriodService.approveSchedulePeriod` sẽ chặn duyệt lịch cho tới khi quản lý bù người — điều này là đúng mong muốn, không cần sửa.

Phải ghi log và huỷ thay đổi cùng nhau: nếu việc gỡ assignment ném lỗi, thì việc duyệt nghỉ phép cũng phải rollback, không được để lại trạng thái nửa vời.

### Luồng công bố lịch

`AdminSchedulePeriodService.publishSchedulePeriod` chèn kiểm tra R3 trước khi chuyển trạng thái. Nếu còn nghỉ phép `PENDING` chồng lên kỳ lịch, ném 409 kèm danh sách các nghỉ phép cần xử lý trước.

### Giao diện

**Trang duyệt nghỉ phép của quản lý** (`SwapRequestsPage`-style, cụ thể là trang quản lý nghỉ phép):

- Sau khi bấm duyệt thành công, hiển thị danh sách ca vừa bị ảnh hưởng: ngày, tên ca, số người cần. Kèm nút điều hướng tới trang sửa lịch của kỳ lịch tương ứng.
- Trước khi bấm duyệt một nghỉ phép `PENDING`, hiển thị cảnh báo R4 liệt kê ca sẽ bị ảnh hưởng nếu duyệt.

**Thông báo tới nhân viên**: khi nghỉ phép được duyệt và có assignment bị gỡ, nhân viên cần biết ca của họ đã thay đổi. Vì các ca bị gỡ đều thuộc lịch `DRAFT`/`APPROVED` mà nhân viên không nhìn thấy, thông báo là kênh duy nhất. Nội dung phải nêu rõ ngày nào bị ảnh hưởng.

Logic hiển thị danh sách ca nên nằm trong một file helpers riêng theo convention của repo, để test được độc lập với việc render.

---

## Kiểm thử

**Backend:**

- Duyệt nghỉ phép, kỳ lịch `DRAFT`, có ca chồng: assignment bị gỡ, trả về đúng danh sách ca, kỳ lịch không đổi.
- Duyệt nghỉ phép, kỳ lịch `PUBLISHED` chồng: 409, không gỡ gì, không đổi trạng thái nghỉ phép.
- Ca thuộc kỳ lịch `PUBLISHED` và kỳ lịch `DRAFT` cùng lúc: R2 phải thắng, nghĩa là 409 chứ không gỡ một phần.
- Gỡ assignment ném lỗi: nghỉ phép rollback về `PENDING`, không ghi log, không gửi thông báo.
- Nghỉ phép không chồng ca nào: vẫn duyệt bình thường, danh sách rỗng.
- Ca qua đêm: ngày nghỉ chỉ một phần ngày của ca thì không bị gỡ — khớp với cách `model_builder.py:23-25` xử lý ca qua đêm bằng trừ 1µs.
- Công bố kỳ lịch có nghỉ phép `PENDING` chồng: 409 kèm danh sách.
- Công bố kỳ lịch không có nghỉ phép `PENDING`: vẫn công bố được.
- Truy vấn tìm ca theo khoảng ngày chỉ trả về ca của đúng nhân viên đó.

**Frontend:**

- Helper dựng danh sách ca bị ảnh hưởng hiển thị đúng ngày, tên ca, số người.
- R4 hiển thị cảnh báo khi nghỉ phép `PENDING` đè lên ca đã có, và không hiển thị khi không đè lên ca nào.
- Thông báo lỗi 409 từ R2 và R3 hiển thị nguyên văn message tiếng Việt của backend, không bị thay bằng lỗi kỹ thuật.

**Kiểm thử tích hợp:** cần ít nhất một test chứng minh kịch bản đầu tiên của phần "Vấn đề hiện tại" nay bị chặn. Đây là kịch bản hiện chưa có test nào bao phủ.

---

## Định nghĩa xong

- Duyệt nghỉ phép trong kỳ lịch chưa công bố: ca của nhân viên bị gỡ, quản lý thấy danh sách ca bị ảnh hưởng và đường dẫn tới trang sửa lịch, nhân viên nhận thông báo nêu rõ ngày bị ảnh hưởng.
- Duyệt nghỉ phép khi lịch đã công bố: bị chặn với thông báo tiếng Việt giải thích lý do.
- Công bố lịch khi còn nghỉ phép chờ duyệt: bị chặn với danh sách cần xử lý.
- Trường hợp gỡ assignment thất bại thì không để lại nghỉ phép ở trạng thái nửa vời.
- `./mvnw.cmd -q test`, `npm.cmd test`, `npm.cmd run build`, `npm.cmd run lint` đều xanh.
