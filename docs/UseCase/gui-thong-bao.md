### 3.1.4.x UseCaseXX Thông báo

#### Đặc tả use case

**Tên use case:** Gửi thông báo

**Mô tả sơ lược:** Chức năng cho phép quản lý gửi thông báo đến nhân viên về lịch trực, yêu cầu đổi ca, ngày nghỉ hoặc các nội dung liên quan đến phân công lịch làm việc.

**Actor chính:** Quản lý

**Actor phụ:** Nhân viên

**Tiền điều kiện:** Quản lý đã đăng nhập thành công vào hệ thống và có quyền gửi thông báo; danh sách nhân viên nhận thông báo tồn tại trong hệ thống.

**Hậu điều kiện:** Một thông báo mới được ghi nhận trong hệ thống và gửi đến các nhân viên được chọn.

#### Luồng sự kiện chính

| Quản lý | Hệ thống | Nhân viên |
| --- | --- | --- |
| 1. Chọn chức năng thông báo | 2. Hiển thị màn hình tạo thông báo | |
| 3. Nhập tiêu đề, nội dung, đối tượng nhận và thời điểm gửi thông báo | 4. Kiểm tra quyền thực hiện, nội dung thông báo và danh sách người nhận | |
| | 5. Hiển thị thông tin thông báo để quản lý xác nhận | |
| 6. Xác nhận gửi thông báo | 7. Ghi nhận thông báo mới vào hệ thống | |
| | 8. Gửi thông báo đến các nhân viên được chọn | 9. Nhận thông báo từ hệ thống |
| | 10. Thông báo gửi thông báo thành công | |

#### Luồng sự kiện thay thế

- 4.a1 Hệ thống kiểm tra quản lý không có quyền gửi thông báo.
- 4.a2 Hệ thống thông báo không đủ quyền thực hiện và quay lại bước 2.
- 4.b1 Hệ thống kiểm tra tiêu đề hoặc nội dung thông báo bị bỏ trống.
- 4.b2 Hệ thống thông báo cần nhập đầy đủ nội dung thông báo và quay lại bước 2.
- 4.c1 Hệ thống kiểm tra danh sách người nhận chưa được chọn.
- 4.c2 Hệ thống thông báo cần chọn ít nhất một nhân viên nhận thông báo và quay lại bước 2.
- 4.d1 Hệ thống kiểm tra danh sách người nhận không có nhân viên hợp lệ.
- 4.d2 Hệ thống thông báo danh sách người nhận không hợp lệ và quay lại bước 2.
- 6.a1 Hệ thống ghi nhận quản lý hủy thao tác gửi thông báo.
- 6.a2 Hệ thống không gửi thông báo và quay lại bước 5.

#### Luồng ngoại lệ

- Không có.
