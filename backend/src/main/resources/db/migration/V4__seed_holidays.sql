INSERT INTO holidays (holiday_date, holiday_name, holiday_year, holiday_type, source_type, source_reference, is_active)
VALUES
    (DATE '2026-01-01', 'Tet Duong lich', 2026, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2026-04-30', 'Ngay Giai phong mien Nam', 2026, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2026-05-01', 'Ngay Quoc te Lao dong', 2026, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2026-09-02', 'Quoc khanh', 2026, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2027-01-01', 'Tet Duong lich', 2027, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2027-04-30', 'Ngay Giai phong mien Nam', 2027, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2027-05-01', 'Ngay Quoc te Lao dong', 2027, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2027-09-02', 'Quoc khanh', 2027, 'PUBLIC_HOLIDAY', 'FIXED_RULE', NULL, TRUE),
    (DATE '2026-02-16', 'Nghi Tet Nguyen dan', 2026, 'LUNAR_HOLIDAY', 'ANNUAL_SEED', 'Thong bao lich nghi nam 2026', TRUE),
    (DATE '2026-02-17', 'Nghi Tet Nguyen dan', 2026, 'LUNAR_HOLIDAY', 'ANNUAL_SEED', 'Thong bao lich nghi nam 2026', TRUE),
    (DATE '2026-04-26', 'Gio To Hung Vuong', 2026, 'LUNAR_HOLIDAY', 'ANNUAL_SEED', 'Thong bao lich nghi nam 2026', TRUE),
    (DATE '2026-09-01', 'Ngay nghi lien ke Quoc khanh', 2026, 'PUBLIC_HOLIDAY', 'ANNUAL_SEED', 'Thong bao lich nghi nam 2026', TRUE),
    (DATE '2026-09-03', 'Ngay nghi bu', 2026, 'SUBSTITUTE_HOLIDAY', 'ANNUAL_SEED', 'Thong bao lich nghi nam 2026', TRUE)
ON CONFLICT (holiday_date) DO UPDATE SET
    holiday_name = EXCLUDED.holiday_name,
    holiday_year = EXCLUDED.holiday_year,
    holiday_type = EXCLUDED.holiday_type,
    source_type = EXCLUDED.source_type,
    source_reference = EXCLUDED.source_reference,
    is_active = EXCLUDED.is_active;
