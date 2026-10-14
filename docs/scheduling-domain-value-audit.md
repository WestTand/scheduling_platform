# Báo cáo đối chiếu miền giá trị lập lịch với code

Ngày kiểm tra: 2026-10-05  
Branch/code: `main`, commit `ebbe010`  
Phạm vi kiểm tra: AI solver (`ai-service/app`), backend scheduling/admin/AI payload (`backend/src/main/java/scheduling/scheduling`), cấu hình ca frontend (`frontend/src/configs/shiftDefinitions.ts`).

## Kết luận nhanh

Đặc tả khớp một phần với hệ thống, nhưng không khớp hoàn toàn. Các lệch quan trọng nhất là tên nhóm ca, giới hạn phút/ngày, biến trung gian `y[e,d,g]`, miền khả dụng `available[e,d,s]`, chu kỳ trực 6 ngày, và ngày lễ trong solver.

Tóm tắt số lượng:

| Mức độ | Số mục | Các mục |
|---|---:|---|
| Khớp | 8 | 1, 2, 3, 5, 8, 10, 12, 16 |
| Khớp một phần | 8 | 4, 7, 13, 14, 17, 18, 19, 20 |
| Không khớp / thiếu | 4 | 6, 9, 11, 15 |

## Bảng đối chiếu 20 mục

| # | Mục đặc tả | Kết luận | Chỗ khớp trong code | Chỗ không khớp / ghi chú |
|---:|---|---|---|---|
| 1 | Nhân viên `e ∈ E`: tập nhân viên đủ điều kiện tham gia phân công | Khớp | `EmployeeInput.employee_id` ở `ai-service/app/schemas/scheduling.py:35-36`; backend lấy nhân viên đủ điều kiện ở `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:148-152`; query điều kiện active ở `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java:27-36` | Tên biến trong code là `employee_id`, không dùng ký hiệu `E`. Điều kiện đủ điều kiện thật: employee `ACTIVE`, account role `EMPLOYEE`, account status `ACTIVE`, `isActive = true`. |
| 2 | Ngày `d ∈ D`: tập ngày trong kỳ lập lịch, định dạng `YYYY-MM-DD` | Khớp | `WorkShiftInput.work_date: date` ở `ai-service/app/schemas/scheduling.py:55-58`; kiểm tra ca nằm trong kỳ ở `ai-service/app/solver/scheduler.py:57` | Code dùng kiểu `date`; khi serialize qua API là ISO `YYYY-MM-DD`. |
| 3 | Tuần `w ∈ W`: tập tuần xét định mức ngoài giờ và thông tầm | Khớp | `week_key()` dùng ISO week ở `ai-service/app/solver/model_builder.py:11-13`; group theo tuần ở `ai-service/app/solver/model_builder.py:86-90` | Tuần là ISO week, không phải tuần tùy chỉnh theo nghiệp vụ khác. |
| 4 | Chu kỳ trực `c ∈ C`: tập chu kỳ sáu ngày | Khớp một phần | Có giá trị cấu hình `ON_CALL_EVERY_N_DAYS = 6` ở `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:109`; solver dùng `on_call_every_n_days` ở `ai-service/app/solver/model_builder.py:152-174` | Không có tập chu kỳ `C`. Code chỉ ràng buộc khoảng cách tối thiểu giữa hai ca trực của cùng nhân viên: nếu cách nhau dưới 6 ngày thì không được phân cả hai. Không bắt buộc mỗi nhân viên có đúng 1 ca trực trong mỗi chu kỳ 6 ngày. |
| 5 | Loại ca `s ∈ S = {THONG_TAM, NGOAI_GIO_1, NGOAI_GIO_2, TRUC_SANG, TRUC_CHIEU, TRUC_DEM}` | Khớp | Frontend định nghĩa đủ 6 mã ở `frontend/src/configs/shiftDefinitions.ts:1-7`; seeder backend định nghĩa đủ 6 mã ở `backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java:142-158` | Tên mã khớp chính xác. |
| 6 | Nhóm ca `g ∈ G = {REGULAR, OUTSIDE_HOURS, ON_CALL}` | Không khớp | Backend entity có `REGULAR` mặc định ở `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java:47-49`; backend cho phép `REGULAR`, `OVERTIME`, `BATHING`, `ON_CALL` ở `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java:144-156`; AI schema nhận `OVERTIME`, `BATHING`, `ON_CALL` hoặc `None` ở `ai-service/app/schemas/scheduling.py:46` | `OUTSIDE_HOURS` không tồn tại. Nhóm ngoài giờ thật là `OVERTIME`. Thông tầm thật là `BATHING`, không phải `REGULAR` ở solver. Backend map `REGULAR` thành `null` trước khi gửi AI ở `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:330-331`. Frontend lại có nhóm hiển thị riêng `THONG_TAM`, `NGOAI_GIO`, `CA_TRUC` ở `frontend/src/configs/shiftDefinitions.ts:9`. |
| 7 | `dayType[d] ∈ {WEEKDAY, WEEKEND, HOLIDAY}`, ngày lễ ưu tiên khi trùng cuối tuần | Khớp một phần | Backend có enum `HOLIDAY`, `WEEKEND`, `WEEKDAY` ở `backend/src/main/java/scheduling/scheduling/admin/DayClassificationService.java:11-15`; `classify()` ưu tiên holiday trước weekend ở `DayClassificationService.java:23-31` | Solver không nhận `dayType`. Objective weekend chỉ tính `shift.work_date.weekday() >= 5` ở `ai-service/app/solver/scheduler.py:316-325`, nên ngày lễ rơi vào thứ thường không được tính như cuối tuần trong penalty. Frontend chỉ có `WEEKDAY` và `WEEKEND_OR_HOLIDAY` ở `frontend/src/configs/shiftDefinitions.ts:11`. |
| 8 | Biến phân công `x[e,d,s] ∈ {0,1}` | Khớp | Biến boolean `assigned[(employee_id, work_shift_id)]` tạo ở `ai-service/app/solver/model_builder.py:35-41` | Chỉ số thật là nhân viên x work-shift instance, không trực tiếp là `(e,d,s)`. Nếu có nhiều work shift cùng ngày/cùng loại, code tách theo `work_shift_id`. |
| 9 | Biến ngày làm `y[e,d,g] ∈ {0,1}`, dùng để đếm định mức theo ngày | Không khớp | Gần nhất là biến `date_assigned` trong quota loop ở `ai-service/app/solver/model_builder.py:138-150` | Không có biến tổng quát `y[e,d,g]`. Code chỉ tạo biến ngày làm riêng trong vòng lặp quota cho `OVERTIME` và `BATHING`; không có cho mọi nhóm `g`, không exposed như miền giá trị chung. |
| 10 | `start_at`, `end_at`: thời điểm đầy đủ ngày giờ, `end_at > start_at` | Khớp | `starts_at`, `ends_at` là `AwareDatetime` ở `ai-service/app/schemas/scheduling.py:55-60`; validator `ends_at > starts_at` ở `ai-service/app/schemas/scheduling.py:63-67`; boundary assignment cũng kiểm tra tương tự ở `ai-service/app/schemas/scheduling.py:98-108` | Tên field code là camelCase qua API (`startsAt`, `endsAt`) và snake_case trong Python (`starts_at`, `ends_at`). |
| 11 | `work_minutes ∈ [1,480]`, tổng phút/ngày mỗi người không vượt quá 480 | Không khớp | AI schema chỉ yêu cầu `duration_minutes > 0` ở `ai-service/app/schemas/scheduling.py:39-41`; backend shift type CRUD giới hạn duration tối đa 8.0 giờ ở `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeRules.java:19,78-87`; frontend canonical workMinutes là 450 hoặc 480 ở `frontend/src/configs/shiftDefinitions.ts:41,57,73,89,105,121` | Solver không enforce `duration_minutes <= 480`. Giới hạn tổng phút/ngày trong solver là `max_work_hours_per_day * 60` ở `ai-service/app/solver/model_builder.py:99-110`; default backend là `MAX_WORK_HOURS_PER_DAY = 12`, tức 720 phút ở `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:105`. |
| 12 | `rest_minutes` từ ca trước đến ca sau tối thiểu 720 phút | Khớp | Default `MIN_REST_HOURS_BETWEEN_SHIFTS = 12` ở `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:104`; solver đổi ra phút ở `ai-service/app/solver/model_builder.py:60`; hàm conflict dùng rest ở `ai-service/app/solver/model_builder.py:16-20` | Khớp số: 12 giờ = 720 phút. |
| 13 | `min_staff`, `max_staff`: `1 ≤ min_staff ≤ max_staff ≤ |E|` | Khớp một phần | AI schema yêu cầu `min_staff > 0`, `max_staff > 0` và `max_staff >= min_staff` ở `ai-service/app/schemas/scheduling.py:44-52`; backend shift type validate `maxStaff >= minStaff` ở `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java:131-134`; work shift requiredStaff nằm trong `[minStaff, maxStaff]` ở `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java:153-158` | Không tìm thấy kiểm tra `max_staff <= |E|`. Solver cũng không dùng `max_staff` để chọn khoảng; mỗi ca bị ép đúng bằng `required_staff` ở `ai-service/app/solver/model_builder.py:43-47`. |
| 14 | Định mức cá nhân: ngoài giờ 1/tuần, thông tầm 2/tuần, trực 1/chu kỳ sáu ngày khi bắt buộc đạt đủ | Khớp một phần | Default đúng số: `OVERTIME_SHIFTS_PER_WEEK = 1`, `BATHING_SHIFTS_PER_WEEK = 2`, `ON_CALL_EVERY_N_DAYS = 6` ở `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:107-109`; solver quota ngày/tuần ở `ai-service/app/solver/model_builder.py:120-150`; on-call gap ở `ai-service/app/solver/model_builder.py:152-174` | Ngoài giờ và thông tầm đếm theo ngày có phân công trong tuần ISO, không đếm số ca. Trực là khoảng cách tối thiểu 6 ngày, không phải chu kỳ exact 1/6 ngày. Không có logic bắt buộc mỗi người phải đạt đủ 1 ca trực trong chu kỳ. |
| 15 | `available[e,d,s] ∈ {0,1}`, bằng 0 nếu không đủ điều kiện hoặc giao nghỉ phép đã duyệt | Không khớp | Nghỉ phép được inline thành `assigned == 0` ở `ai-service/app/solver/model_builder.py:53-58`; rest conflict inline ở `ai-service/app/solver/model_builder.py:60-84` | Không có matrix/input `available[e,d,s]`. Khả dụng được suy ra trong constraint từ approved leaves, boundary assignments và rest conflict. |
| 16 | Phân công khóa: các cặp nhân viên-ca khóa giữ nguyên khi chạy lại | Khớp | Schema `LockedAssignmentInput` ở `ai-service/app/schemas/scheduling.py:92-95`; constraint `assigned == 1` ở `ai-service/app/solver/model_builder.py:49-51`; backend lấy fixed assignments ở `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:187-202` | Khớp. |
| 17 | Biến tổng hợp: tổng số ca, số ca cuối tuần, tổng phút làm mỗi nhân viên | Khớp một phần | Solver tạo `total_shift_count`, `total_work_units`, `weekend_count` ở `ai-service/app/solver/scheduler.py:289-326` | Tổng phút làm trong objective không lưu bằng phút mà đổi sang đơn vị 6 phút: `duration_minutes // 6` ở `ai-service/app/solver/scheduler.py:293-315`. Cuối tuần chỉ là thứ 7/chủ nhật, không bao gồm holiday. |
| 18 | Điểm phạt `P1..P5`: mất cân bằng số ca, ca liên tiếp, cuối tuần, nguyện vọng, giờ làm | Khớp một phần | Penalty thực tế gồm `fairness`, `preference`, `weekend`, `heavyShift`, `workHourBalance` ở `ai-service/app/solver/scheduler.py:267-287,328-356,386-398` | `ca liên tiếp` trong code là `heavyShift`, chỉ phạt hai ca heavy ở hai ngày liên tiếp, không phải mọi ca liên tiếp. Fairness, weekend, workHourBalance dùng spread `max - min`, không phải lệch trung bình. Preference còn nhân thêm priority. |
| 19 | Trọng số `w1..w5 ≥ 0`, `TotalPenalty = Σ(wi × Pi)` | Khớp một phần | Schema trọng số `fairness`, `preference`, `weekend`, `heavy_shift`, `work_hour_balance` với `ge=0` ở `ai-service/app/schemas/scheduling.py:123-128`; default ở `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:110-114`; objective sum ở `ai-service/app/solver/scheduler.py:358-365`; `total_penalty` sum details ở `ai-service/app/solver/scheduler.py:386-400` | Công thức tổng thể khớp, nhưng tên trọng số khác ký hiệu `w1..w5`. Trọng số mặc định: fairness 10, preference 5, weekend 5, heavyShift 8, workHourBalance 10. Preference penalty nhân thêm `priority` ở `ai-service/app/solver/scheduler.py:285-286`. API hiện đọc rule để gửi AI ở `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:275-291`; không thấy endpoint cập nhật rule trong controller hiện tại. |
| 20 | Trạng thái bộ giải `{OPTIMAL, FEASIBLE, INFEASIBLE, MODEL_INVALID, UNKNOWN}` | Khớp một phần | AI response schema khai báo đủ 5 solver status ở `ai-service/app/schemas/scheduling.py:195-198`; solver lấy `solver.status_name(result)` ở `ai-service/app/solver/scheduler.py:371-372`; xử lý success/infeasible/failed ở `ai-service/app/solver/scheduler.py:379-442` | Có thêm outer status riêng: `SUCCESS`, `INFEASIBLE`, `FAILED` ở `ai-service/app/schemas/scheduling.py:195`. `MODEL_INVALID` và `UNKNOWN` đều rơi vào outer `FAILED`, chỉ khác `solver_status` và message. |

## Các điểm đặc tả khớp tốt

| Nội dung | Bằng chứng |
|---|---|
| 6 mã ca chuẩn tồn tại | `frontend/src/configs/shiftDefinitions.ts:1-7`; `backend/src/main/java/scheduling/scheduling/admin/StandardShiftTypeSeeder.java:142-158` |
| Nhân viên đủ điều kiện được lọc trước khi gửi AI | `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java:27-36`; `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:148-152` |
| Biến phân công nhị phân có thật | `ai-service/app/solver/model_builder.py:35-41` |
| Ca bắt buộc đúng `required_staff` người | `ai-service/app/solver/model_builder.py:43-47` |
| Khoảng nghỉ 12 giờ/720 phút có thật | `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:104`; `ai-service/app/solver/model_builder.py:60-84` |
| Phân công khóa được giữ nguyên | `ai-service/app/schemas/scheduling.py:92-95`; `ai-service/app/solver/model_builder.py:49-51` |
| Các trọng số phạt không âm và có objective tổng | `ai-service/app/schemas/scheduling.py:123-128`; `ai-service/app/solver/scheduler.py:358-365` |
| Solver status nội bộ đủ 5 giá trị | `ai-service/app/schemas/scheduling.py:195-198`; `ai-service/app/solver/scheduler.py:371-442` |

## Các điểm đặc tả không khớp hoặc thiếu quan trọng

| Nội dung đặc tả | Thực tế trong code | Mức rủi ro |
|---|---|---|
| Nhóm `OUTSIDE_HOURS` | Code dùng `OVERTIME`; `OUTSIDE_HOURS` không tồn tại | Cao |
| Nhóm `REGULAR` tương ứng thông tầm | Solver dùng `BATHING` cho thông tầm; `REGULAR` bị map thành `null` | Cao |
| Tổng phút/ngày tối đa 480 | Solver mặc định 720 phút/ngày | Cao |
| `work_minutes ∈ [1,480]` | Solver chỉ `> 0`; backend CRUD shift type giới hạn 8 giờ nhưng AI schema không giới hạn 480 | Trung bình |
| `max_staff ≤ |E|` | Không enforce | Trung bình |
| Biến `y[e,d,g]` | Không có biến tổng quát | Trung bình |
| Matrix `available[e,d,s]` | Không có input/structure riêng; chỉ inline constraint | Trung bình |
| Chu kỳ trực `C` | Không có cycle set; chỉ min gap 6 ngày | Cao nếu báo cáo toán học yêu cầu exact cycle |
| Holiday trong solver | Không gửi vào AI; penalty weekend không tính holiday | Cao nếu nghiệp vụ xem holiday như weekend |
| Ca liên tiếp nói chung | Chỉ phạt heavy shift liên tiếp | Trung bình |

## Những thứ code có nhưng đặc tả chưa nhắc

| Nội dung code có | Bằng chứng | Ghi chú |
|---|---|---|
| `boundary_assignments` | `ai-service/app/schemas/scheduling.py:98-108`; backend lấy boundary ở `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:204-214` | Ca trước/sau kỳ vẫn ảnh hưởng rest, quota và giờ làm. |
| Giới hạn số ca/tuần `MAX_SHIFTS_PER_WEEK = 6` | `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulingRuleService.java:106`; constraint ở `ai-service/app/solver/model_builder.py:112-118` | Ràng buộc cứng, đặc tả chưa ghi. |
| Equality coverage `sum x == required_staff` | `ai-service/app/solver/model_builder.py:43-47` | Solver không chọn số người trong khoảng `[min,max]`; work shift đã có `required_staff`. |
| Chẩn đoán infeasible | `_infeasible_diagnostics` và mã lỗi trong `ai-service/app/solver/scheduler.py:100-257` | Đặc tả chưa nhắc hệ thống báo nguyên nhân không xếp được. |
| Solver options | `timeLimitSeconds`, `randomSeed`, `numWorkers` ở `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java:260-270` | Điều khiển thời gian chạy và seed. |
| Preference priority 1-5 | `ai-service/app/schemas/scheduling.py:83-89`; nhân penalty ở `ai-service/app/solver/scheduler.py:285-286` | Làm công thức preference khác `w × P` thuần. |
| `penaltyWeightMultiplier` theo shift type | `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java:66-68`; validate ở `AdminShiftTypeService.java:56,93` | Hiện không được gửi sang AI trong `toShiftTypeDto`, nên là cấu hình chưa dùng trong solver. |

## Khuyến nghị chỉnh đặc tả nếu muốn phản ánh đúng code hiện tại

1. Đổi nhóm ca thành `quota_category ∈ {OVERTIME, BATHING, ON_CALL} ∪ {null}` cho solver; ghi rõ backend còn có `REGULAR` nhưng map thành `null` khi gửi AI.
2. Đổi giới hạn tổng phút/ngày từ 480 thành cấu hình `MAX_WORK_HOURS_PER_DAY × 60`, default 720 phút.
3. Bỏ hoặc ghi rõ `y[e,d,g]` chỉ tồn tại dạng biến phụ `date_assigned` trong quota `OVERTIME/BATHING`, không phải miền chung.
4. Bỏ `available[e,d,s]` như input; đổi thành ràng buộc khả dụng suy ra từ nghỉ phép, boundary assignment và rest conflict.
5. Đổi chu kỳ trực thành ràng buộc khoảng cách: một nhân viên không được có hai ca `ON_CALL` cách nhau dưới `ON_CALL_EVERY_N_DAYS` ngày.
6. Ghi thêm `boundary_assignments` vì đây là input quan trọng ảnh hưởng rest, quota, giờ/ngày và ca/tuần.
7. Ghi thêm `MAX_SHIFTS_PER_WEEK = 6` default và constraint theo tuần ISO.
8. Ghi rõ ngày lễ chỉ được backend dùng khi sinh/lọc ca, còn solver objective weekend hiện chỉ đếm thứ bảy/chủ nhật.

## Khuyến nghị sửa code nếu muốn code bám đúng đặc tả toán học

1. Thống nhất tên nhóm ca: hoặc đổi đặc tả sang `OVERTIME/BATHING/ON_CALL`, hoặc đổi code để có `OUTSIDE_HOURS` và map rõ `REGULAR`.
2. Nếu thật sự muốn 480 phút/ngày, đổi default `MAX_WORK_HOURS_PER_DAY` từ 12 xuống 8 và kiểm tra lại các ca qua đêm/ngoài giờ.
3. Thêm enforce `max_staff <= số nhân viên đủ điều kiện` khi tạo/gửi payload, hoặc ít nhất cảnh báo trước khi optimize.
4. Nếu holiday phải ảnh hưởng công bằng cuối tuần, gửi `dayType` vào AI hoặc gửi danh sách holiday để solver tính `weekend_count` gồm holiday.
5. Nếu cần chu kỳ trực exact 1/6 ngày, thay min-gap bằng mô hình cycle/window và constraint đạt đủ theo chu kỳ.
6. Nếu cần biến `available[e,d,s]` rõ ràng, thêm vào payload hoặc tạo ma trận availability trong model builder trước khi add constraint.
