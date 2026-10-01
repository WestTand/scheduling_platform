CREATE TABLE IF NOT EXISTS employees (
    employee_id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    last_login TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    employee_id BIGINT UNIQUE REFERENCES employees(employee_id)
);

CREATE TABLE IF NOT EXISTS shift_types (
    shift_type_id BIGSERIAL PRIMARY KEY,
    shift_name VARCHAR(50) NOT NULL UNIQUE,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    duration_hours NUMERIC(4, 1) NOT NULL,
    is_overnight BOOLEAN NOT NULL DEFAULT FALSE,
    is_heavy BOOLEAN NOT NULL DEFAULT FALSE,
    min_staff INTEGER NOT NULL,
    max_staff INTEGER NOT NULL,
    quota_category VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    shift_code VARCHAR(30) UNIQUE,
    applicable_weekdays VARCHAR(40) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI,SAT,SUN',
    applies_on_holidays BOOLEAN NOT NULL DEFAULT FALSE,
    penalty_weight_multiplier NUMERIC(3, 2) NOT NULL DEFAULT 1.00
);

CREATE TABLE IF NOT EXISTS holidays (
    holiday_id BIGSERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL UNIQUE,
    holiday_name VARCHAR(150) NOT NULL,
    holiday_year INTEGER NOT NULL,
    holiday_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_reference VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_holidays_date ON holidays(holiday_date);
