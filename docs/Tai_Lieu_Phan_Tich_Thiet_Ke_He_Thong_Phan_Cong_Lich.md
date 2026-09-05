# TÀI LIỆU PHÂN TÍCH THIẾT KẾ VÀ MÔ TẢ DỰ ÁN “WEBSITE PHÂN CÔNG LỊCH LÀM VIỆC”

## I. TỔNG QUAN DỰ ÁN
### 1. Bối cảnh
Tại các phòng ban chuyên môn (như phòng IT, khoa/phòng y tế, trung tâm vận hành system), việc xếp lịch phân công công việc cho nhân sự phát sinh rất nhiều hình thức ca làm việc khác nhau: Làm ngoài giờ, Thông tầm, Trực đêm/ngày [cite: 2]. Việc xếp lịch thủ công bằng Excel thường tốn thời gian, dễ xảy ra sai sót, bị trùng ca, không công bằng giữa các nhân sự hoặc vi phạm các quy định về thời gian nghỉ ngơi [cite: 2]. 

### 2. Mục tiêu đề tài
Dự án yêu cầu xây dựng một phần mềm ứng dụng Trí tuệ nhân tạo (AI - Bài toán thỏa mãn ràng buộc / Tối ưu hóa) để tự động tạo lịch phân công ca trực chuẩn xác, tối ưu và công bằng cho toàn bộ nhân sự trong phòng [cite: 2]. Đối tượng sử dụng bao gồm quản lý và nhân viên [cite: 1].

### 3. Tiêu chí đánh giá dự án
*   **Mô hình AI & Thuật toán (50%):** Thuật toán giải quyết triệt để trùng lịch, đáp ứng đúng định mức (1 ngoài giờ, 2 thông tầm, 1 trực) và xử lý tốt các ràng buộc cứng/mềm [cite: 2].
*   **Giao diện & Trải nghiệm (25%):** Lịch hiển thị trực quan, dễ theo dõi theo từng nhân sự và từng ngày. Hỗ trợ xuất file/in ấn [cite: 2].
*   **Báo cáo & Mô tả bài toán (25%):** Mô tả rõ cách ánh xạ bài toán thực tế thành các biến, miền giá trị và hàm mục tiêu trong AI [cite: 2].

---

## II. YÊU CẦU CHỨC NĂNG HỆ THỐNG
Hệ thống yêu cầu các chức năng quản lý danh sách nhân sự, quản lý định mức ca, module tạo lịch tự động bằng AI, xử lý lịch tự động theo tháng/tuần chỉ với 1 cú click, đưa ra cảnh báo nếu thiếu nhân sự và xuất file Excel/PDF [cite: 2]. Cụ thể các chức năng bao gồm [cite: 1]:

### 1. Chức năng chung
*   Đăng nhập [cite: 1]
*   Đăng xuất [cite: 1]

### 2. Chức năng cho Quản lý
*   **1.1 Quản lý nhân viên:** Xem danh sách, thêm mới, cập nhật thông tin, tìm kiếm, khóa hoặc kích hoạt tài khoản nhân viên [cite: 1].
*   **1.2 Quản lý loại ca trực** [cite: 1]
*   **1.3 Quản lý ngày nghỉ** [cite: 1]
*   **1.4 Quản lý nguyện vọng** [cite: 1]
*   **1.5 Tạo lịch tự động** [cite: 1]
*   **1.6 Điều chỉnh lịch** [cite: 1]
*   **1.7 Duyệt và công bố lịch** [cite: 1]
*   **1.8 Xử lý yêu cầu đổi ca** [cite: 1]
*   **1.9 Xuất dữ liệu** [cite: 1]
*   **1.10 Thông báo** [cite: 1]
*   **1.11 Quản lý hoạt động** [cite: 1]

### 3. Chức năng cho Nhân viên
*   **4.1 Xem lịch trực** [cite: 1]
*   **4.2 Đăng ký ngày nghỉ** [cite: 1]
*   **4.3 Đăng ký nguyện vọng** [cite: 1]
*   **4.4 Gửi yêu cầu đổi ca** [cite: 1]

---

## III. YÊU CẦU PHI CHỨC NĂNG
Hệ thống cần đáp ứng các yêu cầu phi chức năng sau [cite: 1]:
1. Hiệu năng [cite: 1]
2. Độ chính xác và hợp lệ [cite: 1]
3. Bảo mật [cite: 1]
4. Tính sẵn sàng và ổn định [cite: 1]
5. Khả năng sử dụng [cite: 1]
6. Khả năng mở rộng [cite: 1]
7. Khả năng bảo trì [cite: 1]
8. Tính tương thích [cite: 1]
9. Toàn vẹn dữ liệu [cite: 1]
10. Khả năng kiểm tra và truy vết [cite: 1]
11. Yêu cầu sao lưu và phục hồi [cite: 1]

---

## IV. BÀI TOÁN TẠO LỊCH VÀ THUẬT TOÁN AI
Hệ thống sử dụng **Google OR-Tools CP-SAT** để giải bài toán phân công lịch làm việc [cite: 1].

### 1. Ràng buộc cứng (Hard Constraints - BẮT BUỘC KHÔNG VI PHẠM)
*   **Định mức ca/tuần của từng nhân sự:** Làm Ngoài giờ: 1 ngày/tuần; Làm Thông tầm: 2 ngày/tuần; Làm Trực: 1 ngày/6 ngày [cite: 2]. (Hoặc theo hệ thống cấu hình: Ngoài giờ: 1 ngày/tuần, Thông tầm: 2 ngày/tuần [cite: 1]).
*   **Không trùng ca:** Một nhân sự không được phân trùng 2 hình thức làm việc khác nhau có thời gian giao nhau [cite: 1, 2].
*   **Khoảng cách tối thiểu giữa các ca / Thời gian nghỉ:** Khoảng cách từ thời điểm kết thúc ca trước đến thời điểm bắt đầu ca sau phải ít nhất 12 giờ [cite: 1, 2].
*   **Giới hạn giờ làm:** Tổng thời gian làm việc của mỗi nhân viên không vượt quá 12 giờ trong một ngày [cite: 1].
*   **Đủ nhân sự cho ca:** Tổng số nhân viên được phân vào mỗi ca phải nằm trong giới hạn yêu cầu (tối thiểu 10, tối đa 20 người tùy cấu hình) [cite: 1].
*   **Ngày nghỉ đã duyệt:** Nhân viên không được phân ca trong thời gian nghỉ đã được quản lý phê duyệt [cite: 1].
*   **Phân công bị khóa:** Những phân công được quản lý khóa khi điều chỉnh lịch phải được giữ nguyên trong lần chạy lại [cite: 1].
*   **Tính hợp lệ dữ liệu:** Chỉ nhân viên và loại ca đang hoạt động mới được đưa vào lịch; ngày phân công phải nằm trong kỳ lập lịch [cite: 1].

### 2. Ràng buộc mềm (Soft Constraints - TỐI ƯU ƯU TIÊN)
*   **Phân bổ công bằng:** Rải đều các ca Trực hoặc Ngoài giờ rơi vào cuối tuần/ngày lễ giữa tất cả nhân sự [cite: 2].
*   **Tránh dồn ca (ConsecutiveShiftPenalty):** Không phân công 2 ca nặng (như Trực và Thông tầm) vào 2 ngày liên tiếp cho cùng một người nếu còn nhân sự khác thay thế [cite: 1, 2].
*   **Fairness Penalty:** Phạt độ chênh lệch tổng số ca giữa các nhân viên so với mức trung bình [cite: 1].
*   **Weekend Penalty:** Phạt sự mất cân bằng số ca thứ Bảy, Chủ nhật giữa các nhân viên [cite: 1].
*   **Preference Penalty:** Phạt khi lịch không đáp ứng nguyện vọng muốn làm hoặc không muốn làm [cite: 1].
*   **WorkHourImbalance:** Phạt độ chênh lệch tổng số giờ làm giữa các nhân viên [cite: 1].

### 3. Hàm mục tiêu tổng quát
Phương án có `TotalPenalty` càng thấp thì chất lượng lịch càng tốt [cite: 1]:
`TotalPenalty = w1 × Fairness Penalty + w2 × ConsecutiveShiftPenalty + w3 × WeekendPenalty + w4 × PreferencePenalty + w5 × WorkHourImbalance` [cite: 1]
*(Trong đó w1...w5 là trọng số thể hiện mức độ quan trọng do quản lý cấu hình [cite: 1])*

### 4. Xử lý khi không thể tạo lịch
Trường hợp không tìm được lịch thỏa mãn 100% (do thiếu nhân sự), AI phải đưa ra cảnh báo điểm nghẽn và gợi ý phương án điều chỉnh [cite: 2]. Nếu thuật toán trả về INFEASIBLE, hệ thống không bỏ qua ràng buộc cứng mà phân tích và trả về nguyên nhân (như thiếu nhân sự, quá nhiều người nghỉ, định mức không phù hợp...) để quản lý xử lý [cite: 1].

---

## V. CÔNG NGHỆ SỬ DỤNG VÀ KIẾN TRÚC

### 1. Front-end
*   **Ngôn ngữ/Framework:** ReactJS, HTML5, CSS3, Javascript, Typescript [cite: 1].
*   **Thư viện:** FullCalendar, Axios, React Router, Material UI [cite: 1].
*   **Thiết kế:** Figma [cite: 1].

### 2. Back-end
*   **Java Backend:** Java, Spring Boot, Spring Security và JWT, Data JPA và Hibernate, Maven [cite: 1].
*   **Dịch vụ Tối ưu (AI):** Python, FastAPI, Google OR-Tools CP-SAT [cite: 1].

### 3. Cơ sở dữ liệu và Công cụ
*   **Database:** PostgreSQL [cite: 1].
*   **Công cụ:** VSCode, NetBeans, Eclipse, Docker, Docker Compose, Git/GitHub, Postman, Swagger/OpenAPI, pgAdmin [cite: 1].
*   **Kiểm thử:** JUnit, Mockito, Pytest, Jest [cite: 1].

---

## VI. THIẾT KẾ CƠ SỞ DỮ LIỆU
Cơ sở dữ liệu được thiết kế theo mô hình quan hệ và triển khai bằng PostgreSQL [cite: 1].

| Bảng | Mục đích | Thuộc tính chính |
| :--- | :--- | :--- |
| **roles** | Vai trò sử dụng hệ thống [cite: 1]. | role_id (PK), role_name, description [cite: 1] |
| **employees** | Hồ sơ nhân viên [cite: 1]. | employee_id (PK), employee_code, full_name, email, phone, status... [cite: 1] |
| **accounts** | Tài khoản đăng nhập và phân quyền [cite: 1]. | account_id (PK), employee_id (FK), username, password_hash... [cite: 1] |
| **shift_types** | Khai báo loại ca và định mức nhân sự [cite: 1]. | shift_type_id (PK), shift_name, start_time, end_time, duration_hours... [cite: 1] |
| **schedule_periods** | Đợt lập lịch theo tuần/tháng và phiên bản lịch [cite: 1]. | period_id (PK), start_date, end_date, version_no, total_penalty... [cite: 1] |
| **work_shifts** | Ca làm việc cụ thể phát sinh theo ngày [cite: 1]. | work_shift_id (PK), period_id (FK), shift_type_id (FK), work_date... [cite: 1] |
| **shift_assignments** | Kết quả phân nhân viên vào ca [cite: 1]. | assignment_id (PK), work_shift_id (FK), employee_id (FK)... [cite: 1] |
| **leave_requests** | Đăng ký nghỉ và kết quả duyệt [cite: 1]. | leave_request_id (PK), employee_id (FK), start_date, end_date... [cite: 1] |
| **preferences** | Nguyện vọng về ngày/ca làm việc [cite: 1]. | preference_id (PK), employee_id (FK), preference_date, priority... [cite: 1] |
| **swap_requests** | Yêu cầu đổi ca giữa các nhân viên [cite: 1]. | swap_request_id (PK), requester_assignment_id (FK), reason, status... [cite: 1] |
| **optimization_runs** | Lịch sử các lần chạy thuật toán [cite: 1]. | run_id (PK), period_id (FK), weights_json, total_penalty, status... [cite: 1] |
| **scheduling_rules** | Cấu hình ràng buộc xếp lịch [cite: 1]. | rule_id (PK), rule_code, rule_type, rule_value_json, weight... [cite: 1] |
