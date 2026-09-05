### 3.1.4.x UseCaseXX Tạo lịch

#### Đặc tả use case

**Tên use case:** Tạo lịch tự động

**Mô tả sơ lược:** Chức năng cho phép quản lý tạo lịch phân công ca trực tự động theo tuần hoặc tháng dựa trên dữ liệu nhân sự, loại ca, ngày nghỉ, nguyện vọng và các ràng buộc lập lịch.

**Actor chính:** Quản lý

**Actor phụ:** Dịch vụ tối ưu AI

**Tiền điều kiện:** Quản lý đã đăng nhập thành công vào hệ thống và có quyền tạo lịch tự động; dữ liệu nhân sự, loại ca trực và ràng buộc lập lịch đã được cấu hình.

**Hậu điều kiện:** Một lịch phân công dự kiến được tạo ra và lưu trong hệ thống để quản lý tiếp tục điều chỉnh, duyệt hoặc công bố.

#### Luồng sự kiện chính

| Quản lý | Hệ thống | Dịch vụ tối ưu AI |
| --- | --- | --- |
| 1. Chọn chức năng tạo lịch tự động | 2. Hiển thị màn hình cấu hình kỳ lập lịch | |
| 3. Nhập thông tin kỳ lập lịch, phạm vi thời gian, ràng buộc và nhấn tạo lịch | 4. Kiểm tra quyền thực hiện, dữ liệu đầu vào và dữ liệu lập lịch liên quan | |
| | 5. Tổng hợp dữ liệu lập lịch và gửi yêu cầu tạo lịch tối ưu | 6. Phân tích ràng buộc và tạo phương án lịch phù hợp |
| | | 7. Trả về kết quả lịch dự kiến, điểm đánh giá và cảnh báo nếu có |
| | 8. Kiểm tra kết quả tạo lịch từ dịch vụ tối ưu AI | |
| | 9. Hiển thị lịch phân công dự kiến cho quản lý xem xét | |
| 10. Xác nhận lưu lịch dự kiến | 11. Lưu lịch dự kiến và lịch sử lần tạo lịch | |
| | 12. Thông báo tạo lịch tự động thành công | |

#### Luồng sự kiện thay thế

- 4.a1 Hệ thống kiểm tra quản lý không có quyền tạo lịch tự động.
- 4.a2 Hệ thống thông báo không đủ quyền thực hiện và quay lại bước 2.
- 4.b1 Hệ thống kiểm tra thông tin kỳ lập lịch không hợp lệ hoặc bị trùng với kỳ lập lịch đã tồn tại.
- 4.b2 Hệ thống thông báo dữ liệu kỳ lập lịch không hợp lệ và quay lại bước 2.
- 4.c1 Hệ thống kiểm tra dữ liệu nhân sự, loại ca trực, ngày nghỉ hoặc ràng buộc lập lịch chưa đầy đủ.
- 4.c2 Hệ thống thông báo dữ liệu lập lịch chưa đầy đủ và yêu cầu quản lý bổ sung thông tin trước khi quay lại bước 2.
- 8.a1 Hệ thống kiểm tra dịch vụ tối ưu AI không tìm được phương án lịch thỏa mãn các ràng buộc bắt buộc.
- 8.a2 Hệ thống hiển thị nguyên nhân và gợi ý điều chỉnh ràng buộc, sau đó quay lại bước 2.
- 8.b1 Hệ thống kiểm tra kết quả lịch có cảnh báo về thiếu nhân sự hoặc mức độ tối ưu chưa đạt yêu cầu.
- 8.b2 Hệ thống hiển thị cảnh báo để quản lý xem xét và tiếp tục tại bước 9.
- 10.a1 Hệ thống ghi nhận quản lý không xác nhận lưu lịch dự kiến.
- 10.a2 Hệ thống không lưu lịch dự kiến và quay lại bước 9.

#### Luồng ngoại lệ

- 5.e1 Dịch vụ tối ưu AI không phản hồi hoặc xảy ra lỗi trong quá trình tạo lịch.
- 5.e2 Hệ thống thông báo không thể tạo lịch tại thời điểm hiện tại và quay lại bước 2.
