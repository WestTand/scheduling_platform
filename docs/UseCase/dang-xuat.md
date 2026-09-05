### 3.1.4.x UseCaseXX Đăng xuất

#### Đặc tả use case

**Tên use case:** Đăng xuất

**Mô tả sơ lược:** Chức năng cho phép người dùng kết thúc phiên làm việc hiện tại trên website phân công lịch làm việc.

**Actor chính:** Người dùng

**Actor phụ:** Không có

**Tiền điều kiện:** Người dùng đã đăng nhập thành công vào hệ thống và đang có thông tin xác thực JWT hợp lệ.

**Hậu điều kiện:** Thông tin xác thực JWT của người dùng không còn được sử dụng cho phiên làm việc hiện tại và người dùng không còn quyền truy cập vào các chức năng yêu cầu đăng nhập.

#### Luồng sự kiện chính

| Người dùng | Hệ thống |
| --- | --- |
| 1. Chọn chức năng đăng xuất | 2. Hiển thị thông báo xác nhận đăng xuất |
| 3. Xác nhận đăng xuất | 4. Kiểm tra thông tin xác thực JWT của người dùng |
| | 5. Kết thúc trạng thái đăng nhập hiện tại của người dùng |
| | 6. Chuyển người dùng về màn hình đăng nhập |

#### Luồng sự kiện thay thế

- 3.a1 Người dùng hủy thao tác đăng xuất.
- 3.a2 Hệ thống giữ nguyên phiên làm việc hiện tại và quay lại màn hình trước đó.
- 4.a1 Hệ thống kiểm tra thông tin xác thực JWT không còn hợp lệ.
- 4.a2 Hệ thống thông báo phiên đăng nhập đã hết hạn và chuyển người dùng về màn hình đăng nhập.

#### Luồng ngoại lệ

- Không có.
