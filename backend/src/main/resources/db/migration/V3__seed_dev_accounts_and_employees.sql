INSERT INTO employees (employee_code, full_name, email, phone, status)
VALUES
    ('EMP001', 'Nguyễn Văn An', 'emp001@example.com', '0900000001', 'ACTIVE'),
    ('EMP002', 'Trần Thị Bình', 'emp002@example.com', '0900000002', 'ACTIVE'),
    ('EMP003', 'Lê Minh Châu', 'emp003@example.com', '0900000003', 'ACTIVE'),
    ('EMP004', 'Phạm Quốc Dũng', 'emp004@example.com', '0900000004', 'ACTIVE'),
    ('EMP005', 'Hoàng Thu Hà', 'emp005@example.com', '0900000005', 'ACTIVE'),
    ('EMP006', 'Đỗ Minh Khang', 'emp006@example.com', '0900000006', 'ACTIVE'),
    ('EMP007', 'Vũ Ngọc Lan', 'emp007@example.com', '0900000007', 'ACTIVE'),
    ('EMP008', 'Ngô Đức Long', 'emp008@example.com', '0900000008', 'ACTIVE'),
    ('EMP009', 'Bùi Khánh Linh', 'emp009@example.com', '0900000009', 'ACTIVE'),
    ('EMP010', 'Đặng Quang Minh', 'emp010@example.com', '0900000010', 'ACTIVE'),
    ('EMP011', 'Phan Thảo My', 'emp011@example.com', '0900000011', 'ACTIVE'),
    ('EMP012', 'Tạ Hữu Nam', 'emp012@example.com', '0900000012', 'ACTIVE'),
    ('EMP013', 'Lý Bảo Ngọc', 'emp013@example.com', '0900000013', 'ACTIVE'),
    ('EMP014', 'Mai Hoài Phương', 'emp014@example.com', '0900000014', 'ACTIVE'),
    ('EMP015', 'Cao Thanh Sơn', 'emp015@example.com', '0900000015', 'ACTIVE'),
    ('EMP016', 'Trịnh Mai Trang', 'emp016@example.com', '0900000016', 'ACTIVE'),
    ('EMP017', 'Hà Tuấn Tú', 'emp017@example.com', '0900000017', 'ACTIVE'),
    ('EMP018', 'Lưu Gia Huy', 'emp018@example.com', '0900000018', 'ACTIVE'),
    ('EMP019', 'Nguyễn Hải Yến', 'emp019@example.com', '0900000019', 'ACTIVE'),
    ('EMP020', 'Chu Việt Anh', 'emp020@example.com', '0900000020', 'ACTIVE')
ON CONFLICT (employee_code) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO accounts (username, password_hash, role, is_active, status, employee_id)
VALUES ('manager@example.com', '$2a$10$9Uxh0PAt8Qhlib16W30HMO9sTFXbXt4FGBSzUfcs.547vYRjAyw0e', 'MANAGER', TRUE, 'ACTIVE', NULL)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    status = EXCLUDED.status,
    employee_id = NULL;

INSERT INTO accounts (username, password_hash, role, is_active, status, employee_id)
SELECT employee.email, '$2a$10$8AAQl.It1RVVXhrkWlHrOubpixsMBedBuK5LrpY/PyM5UkqNtTvN2', 'EMPLOYEE', TRUE, 'ACTIVE', employee.employee_id
FROM employees employee
WHERE employee.employee_code BETWEEN 'EMP001' AND 'EMP020'
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    status = EXCLUDED.status,
    employee_id = EXCLUDED.employee_id;
