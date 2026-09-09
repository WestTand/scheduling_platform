# Project Plan - Website Phan Cong Lich Lam Viec

## Trello Lists

- Backlog
- Phase 0 - Setup
- Phase 1 - Database & Entity
- Phase 2 - Auth & Security
- Phase 3 - Admin Core
- Phase 4 - Employee Core
- Phase 5 - Scheduling Manual
- Phase 6 - AI Scheduling
- Phase 7 - Notification & Logs
- Phase 8 - Export & Report
- Phase 9 - Frontend Integration
- Phase 10 - Testing & Demo
- Done

## Phase 0 - Setup

- Khoi tao monorepo `frontend`, `backend`, `ai-service`, `infra`
- Cau hinh Docker Compose cho PostgreSQL, backend, frontend, ai-service
- Tao health check backend `/api/health`
- Tao health check AI service `/health`
- Setup React + Vite + TypeScript
- Setup Spring Boot backend
- Setup FastAPI AI service
- Setup README huong dan chay local
- Verify chay duoc toan bo stack bang Docker

## Phase 1 - Database & Entity

- Chot database design v2
- Tao entity `Account`
- Tao entity `Employee`
- Bo sung trang thai account: pending activation, active, inactive, force password change
- Tao entity/token luu OTP kich hoat tai khoan
- Tao entity `ShiftType`
- Tao entity `SchedulePeriod`
- Tao entity `WorkShift`
- Tao entity `ShiftAssignment`
- Tao entity `LeaveRequest`
- Tao entity `Preference`
- Tao entity `SchedulingRule`
- Tao entity `OptimizationRun`
- Tao entity `SwapRequest`
- Tao entity `Notification`
- Tao entity `ActivityLog`
- Tao entity `RefreshToken`
- Tao repository cho cac entity chinh
- Tao repository cho OTP kich hoat tai khoan
- Kiem tra mapping PK dung theo tai lieu
- Kiem tra unique constraint quan trong
- Kiem tra default value entity
- Verify backend tests

## Phase 2 - Auth & Security

- Them dependency JWT
- Them dependency gui mail neu can
- Them config JWT secret, access token TTL, refresh token TTL
- Them config SMTP/mail sender cho moi truong dev/prod
- Bo public self-registration cua nhan vien
- Implement login bang username/password
- Neu account chua kich hoat, login tra ve trang thai can xac thuc OTP
- Neu account bi khoa, login bi tu choi
- Implement gui OTP kich hoat tai khoan qua email
- Implement verify OTP kich hoat tai khoan
- Implement resend OTP kich hoat tai khoan
- Implement bat buoc doi mat khau lan dau sau khi OTP hop le
- Implement bat buoc doi mat khau sau khi quan ly reset mat khau
- Hash password bang BCrypt
- Generate access token JWT
- Luu refresh token hash vao DB
- Set refresh token vao `HttpOnly Cookie`
- Implement refresh token rotation
- Implement revoke refresh token khi logout
- Implement `/api/auth/me`
- Chan inactive account login/refresh/me
- Chan account chua kich hoat refresh/me
- Revoke refresh token cu khi account bi reset mat khau
- Cau hinh Spring Security stateless
- Public `/api/auth/login`, `/api/auth/activate/verify-otp`, `/api/auth/activate/resend-otp`, `/api/auth/password/change-required`, `/api/auth/refresh`, `/api/auth/logout`, `/api/health`
- Protect cac API con lai bang Bearer token
- Viet test `AuthService`
- Viet test `AuthController`
- Viet test OTP activation flow
- Viet test force change password flow
- Push branch auth len GitHub

## Phase 3 - Admin Core

- API danh sach nhan vien
- API them nhan vien kem tao account bang email
- API cap nhat nhan vien
- API khoa/mo khoa nhan vien
- API tim kiem nhan vien
- API danh sach tai khoan
- API quan ly tao account nhan vien voi email
- API gui lai OTP kich hoat cho nhan vien
- API reset mat khau tam cho nhan vien
- API set account ve trang thai can kich hoat/doi mat khau lai sau reset
- API revoke refresh tokens cua nhan vien sau reset/khoa account
- API khoa account nhan vien
- API mo khoa account nhan vien
- API quan ly loai ca truc
- API them loai ca
- API cap nhat loai ca
- API khoa/mo khoa loai ca
- API quan ly scheduling rules
- API cau hinh dinh muc ca/tuan
- API cau hinh trong so soft constraints
- Test admin employee APIs
- Test admin account creation APIs
- Test admin password reset/reactivation APIs
- Test shift type APIs
- Test scheduling rule APIs

## Phase 4 - Employee Core

- API nhan vien xem profile ca nhan
- API nhan vien xem lich cua minh
- API dang ky ngay nghi
- API xem danh sach ngay nghi da gui
- API huy yeu cau nghi neu con pending
- API dang ky nguyen vong muon lam/khong muon lam
- API xem danh sach nguyen vong
- API cap nhat nguyen vong pending
- API gui yeu cau doi ca
- API xem yeu cau doi ca lien quan den minh
- Test leave request flow
- Test preference flow
- Test swap request flow

## Phase 5 - Scheduling Manual

- API tao ky lap lich `SchedulePeriod`
- API tao work shifts theo ky
- API xem lich theo ky
- API xem lich theo nhan vien
- API phan cong nhan vien vao ca thu cong
- API cap nhat phan cong
- API khoa phan cong `is_locked`
- API mo khoa phan cong
- API kiem tra trung ca khi phan cong thu cong
- API kiem tra gioi han gio lam/ngay
- API duyet lich
- API cong bo lich
- Test tao ky lap lich
- Test phan cong thu cong
- Test duyet/cong bo lich

## Phase 6 - AI Scheduling

- Thiet ke request payload tu backend sang AI service
- API backend goi AI service tao lich
- AI service nhan danh sach nhan vien active
- AI service nhan shift types active
- AI service nhan approved leave requests
- AI service nhan preferences
- AI service nhan locked assignments
- Implement hard constraint: du nhan su moi ca
- Implement hard constraint: khong trung ca
- Implement hard constraint: nghi toi thieu 12 gio
- Implement hard constraint: toi da 12 gio/ngay
- Implement hard constraint: khong phan ca vao ngay nghi da duyet
- Implement hard constraint: giu phan cong bi khoa
- Implement quota: ngoai gio 1 ngay/tuan
- Implement quota: thong tam 2 ngay/tuan
- Implement quota: truc 1 ngay/6 ngay neu ap dung
- Implement soft constraint: fairness penalty
- Implement soft constraint: consecutive heavy shift penalty
- Implement soft constraint: weekend penalty
- Implement soft constraint: preference penalty
- Implement soft constraint: work hour imbalance
- Tra ket qua lich toi uu ve backend
- Backend luu `ShiftAssignment`
- Backend luu `OptimizationRun`
- Backend luu `total_penalty`
- Xu ly INFEASIBLE va tra ly do
- Test AI service bang pytest
- Test backend integration voi mocked AI service

## Phase 7 - Notification & Logs

- Tao service ghi `ActivityLog`
- Ghi log khi tao/sua/xoa nhan vien
- Ghi log khi duyet nghi
- Ghi log khi tao lich tu dong
- Ghi log khi dieu chinh lich
- Ghi log khi cong bo lich
- Ghi log khi xu ly doi ca
- Tao notification khi lich duoc cong bo
- Tao notification khi don nghi duoc duyet/tu choi
- Tao notification khi co yeu cau doi ca
- Tao notification khi yeu cau doi ca duoc duyet/tu choi
- API xem danh sach notification
- API danh dau notification da doc
- API xem activity logs cho quan ly
- Test notification service
- Test activity log service

## Phase 8 - Export & Report

- API xuat lich Excel
- API xuat lich PDF neu can
- Export lich theo ky
- Export lich theo nhan vien
- Export bao cao tong so ca moi nhan vien
- Export bao cao so gio lam moi nhan vien
- Export bao cao penalty/optimization result
- UI nut export Excel/PDF
- Test export Excel
- Test export du lieu dung filter

## Phase 9 - Frontend Integration

- Setup routing
- Tao layout chung
- Tao trang login
- Bo trang register public cho nhan vien
- Tao trang nhap OTP kich hoat tai khoan
- Tao trang resend OTP kich hoat
- Tao trang doi mat khau bat buoc lan dau
- Tao trang thong bao quen mat khau: lien he quan ly de cap lai
- Luu access token phia frontend
- Goi refresh token bang cookie
- Axios interceptor attach Bearer token
- Axios interceptor tu refresh khi access token expired
- Route guard cho protected pages
- Role guard cho manager pages
- Trang dashboard quan ly
- Trang quan ly nhan vien
- Form quan ly tao nhan vien kem email/account
- Form quan ly reset mat khau tam cho nhan vien
- Nut quan ly gui lai OTP kich hoat
- Nut quan ly khoa/mo khoa account
- Trang quan ly loai ca
- Trang quan ly ngay nghi
- Trang quan ly nguyen vong
- Trang tao lich tu dong
- Trang xem lich dang calendar/table
- Trang dieu chinh lich thu cong
- Trang duyet va cong bo lich
- Trang xu ly doi ca
- Trang thong bao
- Trang activity logs
- Trang nhan vien xem lich
- Trang nhan vien dang ky nghi
- Trang nhan vien dang ky nguyen vong
- Trang nhan vien gui doi ca
- Responsive desktop/mobile
- Test frontend build

## Phase 10 - Testing & Demo

- Viet seed data demo
- Seed manager account
- Seed danh sach nhan vien mau
- Seed account nhan vien chua kich hoat
- Seed account nhan vien can doi mat khau
- Seed loai ca mau: ngoai gio, thong tam, truc
- Seed scheduling rules mau
- Test flow quan ly tao account nhan vien
- Test flow nhan vien nhan OTP email kich hoat
- Test flow nhan vien doi mat khau lan dau
- Test flow login/refresh/logout
- Test flow quen mat khau: nhan vien lien he quan ly
- Test flow quan ly reset mat khau va gui lai OTP
- Test flow quan ly nhan vien
- Test flow dang ky nghi
- Test flow dang ky nguyen vong
- Test flow tao lich tu dong
- Test flow dieu chinh lich
- Test flow duyet/cong bo lich
- Test flow doi ca
- Test flow notification
- Test export Excel/PDF
- Kiem tra Docker compose chay sach
- Viet huong dan demo
- Chuan bi du lieu demo cho giang vien
- Chup man hinh minh hoa
- Hoan thien bao cao mo ta bai toan AI
- Hoan thien mapping bien, mien gia tri, constraints, objective function
- Tong kiem tra truoc nop

## Priority Goi Y

- P0: Setup, Entity, Auth, Admin employee, Shift type, Scheduling period, AI scheduling core
- P1: Leave request, Preference, Manual adjustment, Publish schedule, Export
- P2: Notification, Activity log, PDF, UI polish
- P3: Extra reports, advanced filters, cleanup

## Milestone Goi Y

- Milestone 1: Chay duoc he thong va auth
- Milestone 2: Quan ly duoc nhan vien, loai ca, ngay nghi, nguyen vong
- Milestone 3: Tao duoc lich thu cong va cong bo lich
- Milestone 4: Tao lich tu dong bang AI
- Milestone 5: Hoan thien UI, export, bao cao, demo

## Trang Thai Hien Tai

- Phase 0: Gan xong, da co monorepo, Docker setup, backend/frontend/AI health scaffold.
- Phase 1: Entity chinh da co va da fix mapping theo database design v2. Da bo sung `AccountStatus` gom `ACTIVE`, `PENDING_ACTIVATION`, `FORCE_PASSWORD_CHANGE`, `INACTIVE` vao `Account`.
- Phase 2: Auth/JWT/refresh cookie da merge tu branch `feature/auth-jwt-refresh-cookie`. Tren branch `feature/account-lifecycle` da bo public self-registration `/api/auth/register`, them `spring-boot-starter-mail`, them config SMTP/Gmail qua env, them role `ADMIN`, va them endpoint `POST /api/manager/accounts` de `MANAGER`/`ADMIN` tao account nhan vien voi password mac dinh lay tu bien moi truong `DEFAULT_EMPLOYEE_PASSWORD` va `status = PENDING_ACTIVATION`.
- Phase 2 con thieu: flow login cho account `PENDING_ACTIVATION` tra ve trang thai can OTP, gui/verify/resend OTP qua email, bat buoc doi mat khau sau OTP/reset, chan refresh/me cho account chua kich hoat, revoke refresh token khi reset mat khau, va test day du cho OTP/force password change.
- Phase 3: Da bat dau phan account creation API bang endpoint `POST /api/manager/accounts`. Cac API admin core khac chua lam.

## Commit Da Push - `feature/account-lifecycle`

- `614d56e feat: add account lifecycle status`
- `01df1d8 chore: configure spring mail sender`
- `671577c fix: remove public self registration`
- `d671d05 docs: design manager-created employee accounts`
- `ccee8fa docs: plan manager-created employee accounts`
- `568ea99 feat: add admin account role`
- `410271c feat: add manager employee account service`
- `7f92ea8 feat: expose manager employee account endpoint`

## Verification Gan Nhat

- Backend command: `./mvnw.cmd clean test`
- Ket qua gan nhat tren branch `feature/account-lifecycle`: `44` tests, `0` failures, `0` errors.
