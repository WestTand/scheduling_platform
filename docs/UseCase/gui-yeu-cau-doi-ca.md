### 3.1.4.x UseCaseXX Đổi ca

#### Đặc tả use case

**Tên use case:** Gửi yêu cầu đổi ca

**Mô tả sơ lược:** Chức năng cho phép nhân viên gửi yêu cầu đổi ca làm việc dựa trên nguyện vọng đổi ca đã được đăng ký và chấp thuận trước đó.

**Actor chính:** Nhân viên

**Actor phụ:** Không có

**Tiền điều kiện:** Nhân viên đã đăng nhập thành công vào hệ thống; lịch trực đã được công bố; nhân viên có ca trực hợp lệ và có nguyện vọng đổi ca đã được chấp thuận thông qua chức năng đăng ký nguyện vọng.

**Hậu điều kiện:** Một yêu cầu đổi ca hợp lệ được ghi nhận trong hệ thống để quản lý theo dõi và xử lý ở chức năng xử lý yêu cầu đổi ca.

#### Luồng sự kiện chính

| Nhân viên | Hệ thống |
| --- | --- |
| 1. Chọn chức năng gửi yêu cầu đổi ca | 2. Hiển thị danh sách ca trực hiện tại của nhân viên |
| 3. Chọn ca trực cần đổi và nhập lý do đổi ca | 4. Kiểm tra ca trực, trạng thái lịch và nguyện vọng đổi ca đã được chấp thuận |
| | 5. Hiển thị thông tin yêu cầu đổi ca để nhân viên xác nhận |
| 6. Xác nhận gửi yêu cầu đổi ca | 7. Ghi nhận yêu cầu đổi ca hợp lệ vào hệ thống |
| | 8. Thông báo gửi yêu cầu đổi ca thành công |

#### Luồng sự kiện thay thế

- 4.a1 Hệ thống kiểm tra ca trực được chọn không hợp lệ hoặc không thuộc lịch trực của nhân viên.
- 4.a2 Hệ thống thông báo ca trực không hợp lệ và quay lại bước 2.
- 4.b1 Hệ thống kiểm tra lịch trực chưa được công bố hoặc ca trực không còn cho phép đổi.
- 4.b2 Hệ thống thông báo ca trực chưa đủ điều kiện đổi và quay lại bước 2.
- 4.c1 Hệ thống kiểm tra nhân viên chưa có nguyện vọng đổi ca được chấp thuận.
- 4.c2 Hệ thống thông báo cần đăng ký nguyện vọng đổi ca và được chấp thuận trước khi gửi yêu cầu, sau đó quay lại bước 2.
- 6.a1 Hệ thống ghi nhận nhân viên hủy thao tác gửi yêu cầu đổi ca.
- 6.a2 Hệ thống không tạo yêu cầu đổi ca và quay lại bước 5.

#### Luồng ngoại lệ

- Không có.
