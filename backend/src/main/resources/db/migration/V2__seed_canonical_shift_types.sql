UPDATE shift_types SET shift_code = 'THONG_TAM' WHERE shift_name = 'Thong tam' AND shift_code IS NULL;
UPDATE shift_types SET shift_code = 'NGOAI_GIO_1' WHERE shift_name = 'Ngoai gio 1' AND shift_code IS NULL;
UPDATE shift_types SET shift_code = 'NGOAI_GIO_2' WHERE shift_name = 'Ngoai gio 2' AND shift_code IS NULL;
UPDATE shift_types SET shift_code = 'TRUC_SANG' WHERE shift_name = 'Truc sang' AND shift_code IS NULL;
UPDATE shift_types SET shift_code = 'TRUC_CHIEU' WHERE shift_name = 'Truc chieu' AND shift_code IS NULL;
UPDATE shift_types SET shift_code = 'TRUC_DEM' WHERE shift_name = 'Truc dem' AND shift_code IS NULL;

INSERT INTO shift_types (shift_code, shift_name, quota_category, start_time, end_time, duration_hours, is_overnight, is_heavy, min_staff, max_staff, status, applicable_weekdays, applies_on_holidays, penalty_weight_multiplier)
VALUES
    ('THONG_TAM', 'Thong tam', 'BATHING', TIME '08:00', TIME '17:00', 8.0, FALSE, TRUE, 1, 20, 'ACTIVE', 'MON,TUE,WED,THU,FRI', FALSE, 1.00),
    ('NGOAI_GIO_1', 'Ngoai gio 1', 'OVERTIME', TIME '17:00', TIME '00:30', 7.5, TRUE, FALSE, 1, 20, 'ACTIVE', 'MON,TUE,WED,THU,FRI', FALSE, 1.20),
    ('NGOAI_GIO_2', 'Ngoai gio 2', 'OVERTIME', TIME '00:30', TIME '08:00', 7.5, FALSE, TRUE, 1, 20, 'ACTIVE', 'MON,TUE,WED,THU,FRI', FALSE, 1.50),
    ('TRUC_SANG', 'Truc sang', 'ON_CALL', TIME '08:00', TIME '16:00', 8.0, FALSE, FALSE, 1, 20, 'ACTIVE', 'SAT,SUN', TRUE, 1.00),
    ('TRUC_CHIEU', 'Truc chieu', 'ON_CALL', TIME '16:00', TIME '00:00', 8.0, TRUE, FALSE, 1, 20, 'ACTIVE', 'SAT,SUN', TRUE, 1.20),
    ('TRUC_DEM', 'Truc dem', 'ON_CALL', TIME '00:00', TIME '08:00', 8.0, FALSE, TRUE, 1, 20, 'ACTIVE', 'SAT,SUN', TRUE, 1.50)
ON CONFLICT (shift_code) DO UPDATE SET
    shift_name = EXCLUDED.shift_name,
    quota_category = EXCLUDED.quota_category,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    duration_hours = EXCLUDED.duration_hours,
    is_overnight = EXCLUDED.is_overnight,
    is_heavy = EXCLUDED.is_heavy,
    min_staff = EXCLUDED.min_staff,
    max_staff = EXCLUDED.max_staff,
    status = EXCLUDED.status,
    applicable_weekdays = EXCLUDED.applicable_weekdays,
    applies_on_holidays = EXCLUDED.applies_on_holidays,
    penalty_weight_multiplier = EXCLUDED.penalty_weight_multiplier;
