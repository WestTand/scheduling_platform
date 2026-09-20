# Project Plan - Website Phan Cong Lich Lam Viec

Tai lieu nay la roadmap chinh cua he thong. Moi phase ben duoi ghi ro muc dich, ket qua mong doi, va tac dung cua tung task de khi tiep tuc phat trien co the biet vi sao can lam task do.

## Trello Lists

- Backlog: noi chua cac y tuong/chuc nang chua dua vao phase cu the.
- Phase 0 - Setup: tao khung du an va moi truong chay.
- Phase 1 - Database & Entity: tao nen tang du lieu va mapping domain.
- Phase 2 - Auth & Security: dang nhap, bao mat, phan quyen, vong doi tai khoan.
- Phase 3 - Admin Core: cac API quan tri cot loi cho quan ly nhan su va cau hinh.
- Phase 4 - Employee Core: cac API cho nhan vien tu xem lich, nghi phep, nguyen vong, doi ca.
- Phase 5 - Scheduling Manual: tao va dieu chinh lich thu cong truoc khi co AI.
- Phase 6 - AI Scheduling: tao lich tu dong bang solver/AI service.
- Phase 7 - Notification & Logs: thong bao va audit trail cho cac hanh dong quan trong.
- Phase 8 - Export & Report: xuat file va bao cao phuc vu van hanh/demo.
- Phase 9 - Frontend Integration: noi UI voi backend va hoan thien trai nghiem nguoi dung.
- Phase 10 - Testing & Demo: seed data, test end-to-end, chuan bi demo/bao cao.
- Done: cac task da hoan tat va da verify.

## Current Progress Summary

- Phase 0: Gan xong, da co monorepo, Docker setup, backend/frontend/AI health scaffold. Muc dich la dam bao moi thanh phan co the chay local va trong Docker.
- Phase 1: Entity chinh da co va da fix mapping theo database design v2. Da bo sung `AccountStatus` gom `ACTIVE`, `PENDING_ACTIVATION`, `FORCE_PASSWORD_CHANGE`, `INACTIVE` vao `Account`. Muc dich la tao nen tang database cho auth, nhan su, ca truc, lich, nghi phep, doi ca, thong bao va audit log.
- Phase 2: Auth/JWT/refresh cookie da merge. Da bo public self-registration `/api/auth/register`, them mail config, them role `ADMIN`, manager tao account nhan vien bang password mac dinh, OTP kich hoat, force password change, refresh token rotation/revoke, va chan account khong hop le. Muc dich la bao ve he thong truoc khi mo cac API quan tri.
- Phase 3: Hoan tat backend admin core. Da implement employee admin APIs, manager account APIs, admin account APIs cho list/lock/unlock tai khoan nhan vien, shift type APIs, va scheduling rule APIs.
- Phase 5: Dang lam tren branch `phase5/shift-assignment-apis`. SchedulePeriod APIs cho manager da hoan tat o pham vi `DRAFT`; WorkShift + ShiftAssignment slices da xong (manual assign, period schedule view, delete, lock/unlock).

## Phase 0 - Setup

Muc dich: tao khung monorepo va moi truong phat trien co the chay lap lai. Phase nay giup backend, frontend, AI service va database co health check rieng de phat hien loi moi truong som.

Ket qua mong doi: developer co the clone repo, chay Docker Compose, mo frontend/backend/AI service, va verify tung service dang song.

- [x] Khoi tao monorepo `frontend`, `backend`, `ai-service`, `infra`
  - Dung de tach ro UI, API, AI service va ha tang Docker.
  - Giup moi phan co dependency rieng, de test va deploy rieng sau nay.
- [x] Cau hinh Docker Compose cho PostgreSQL, backend, frontend, ai-service
  - Dung de chay toan bo stack local bang mot lenh.
  - Giam loi khac moi truong giua may dev va demo.
- [x] Tao health check backend `/api/health`
  - Dung de kiem tra backend Spring Boot dang khoi dong va phan hoi.
  - Docker/backend monitor co the dua vao endpoint nay de biet service healthy.
- [x] Tao health check AI service `/health`
  - Dung de kiem tra AI service FastAPI dang song.
  - La nen tang de backend goi AI service o Phase 6.
- [x] Setup React + Vite + TypeScript
  - Dung de xay frontend nhanh, co type safety va build nhe.
  - La nen tang cho Phase 9 frontend integration.
- [x] Setup Spring Boot backend
  - Dung de xay REST API, security, JPA, transaction va business logic.
  - La trung tam xu ly du lieu va orchestration voi AI service.
- [x] Setup FastAPI AI service
  - Dung de tach logic solver/AI khoi backend chinh.
  - Giup Phase 6 co noi rieng de implement thuat toan lap lich.
- [x] Setup README huong dan chay local
  - Dung de nguoi moi vao project biet cach chay, test va debug.
  - Giam thoi gian setup khi demo hoac cham bai.
- [x] Verify chay duoc toan bo stack bang Docker
  - Dung de chung minh setup thuc su hoat dong end-to-end.
  - La baseline truoc khi them chuc nang phuc tap.

## Phase 1 - Database & Entity

Muc dich: tao model du lieu dung voi nghiep vu phan cong lich lam viec. Neu phase nay sai, cac phase sau se kho thao tac vi API, lich va AI deu dua vao entity.

Ket qua mong doi: database co day du bang chinh, khoa chinh/khoa ngoai, constraint quan trong, repository co ban va test mapping.

- [x] Chot database design v2
  - Dung de thong nhat bang nao ton tai, quan he nao bat buoc, va cot nao can unique/not-null.
  - Giam viec sua schema lon khi da co API va frontend.
- [x] Tao entity `Account`
  - Luu thong tin dang nhap, role, trang thai tai khoan va lien ket employee.
  - La goc cho auth, phan quyen, refresh token, OTP va activity log.
- [x] Tao entity `Employee`
  - Luu ho so nhan vien: ma nhan vien, ten, email, phone, status.
  - La doi tuong chinh duoc lap lich, xin nghi, dang ky nguyen vong va doi ca.
- [x] Bo sung trang thai account: pending activation, active, inactive, force password change
  - Dung de quan ly vong doi tai khoan sau khi manager tao/reset.
  - Ngan nhan vien dang nhap khi chua kich hoat hoac chua doi password bat buoc.
- [x] Tao entity/token luu OTP kich hoat tai khoan
  - Luu OTP dang hash, thoi gian het han va trang thai da dung.
  - Dam bao kich hoat tai khoan qua email co audit va het han an toan.
- [x] Tao entity `ShiftType`
  - Dinh nghia loai ca: ten ca, gio bat dau/ket thuc, duration, min/max staff, heavy/overnight.
  - La cau hinh dau vao cho work shifts va AI scheduling.
- [x] Tao entity `SchedulePeriod`
  - Dinh nghia ky lap lich theo khoang ngay va trang thai phe duyet/cong bo.
  - Gom cac work shifts/assignments vao mot dot lap lich de quan ly.
- [x] Tao entity `WorkShift`
  - Dinh nghia mot ca lam cu the trong ngay va thuoc mot ky lap lich.
  - La don vi can gan nhan vien vao trong Phase 5/6.
- [x] Tao entity `ShiftAssignment`
  - Luu viec nhan vien nao duoc gan vao work shift nao.
  - Ho tro khoa assignment thu cong de AI khong ghi de.
- [x] Tao entity `LeaveRequest`
  - Luu don xin nghi cua nhan vien va trang thai duyet.
  - AI/manual scheduling can dung de khong phan ca vao ngay nghi da duyet.
- [x] Tao entity `Preference`
  - Luu nguyen vong muon lam/khong muon lam theo ngay/loai ca.
  - AI dung nhu soft constraint de tao lich hop ly hon.
- [x] Tao entity `SchedulingRule`
  - Luu cau hinh hard/soft constraints va weights.
  - Cho phep manager dieu chinh quy tac lap lich ma khong sua code.
- [x] Tao entity `OptimizationRun`
  - Luu lich su moi lan chay AI: status, score/penalty, error, nguoi chay.
  - Giup audit va debug khi AI khong tao duoc lich.
- [x] Tao entity `SwapRequest`
  - Luu yeu cau doi ca giua nhan vien.
  - La nen tang cho workflow doi ca o Phase 4/7.
- [x] Tao entity `Notification`
  - Luu thong bao cho nhan vien/quan ly.
  - Dung de bao lich moi, don nghi, doi ca, reset/kich hoat neu can.
- [x] Tao entity `ActivityLog`
  - Luu hanh dong quan trong cua user/system.
  - Ho tro audit khi quan ly thay doi lich, nhan vien, don nghi.
- [x] Tao entity `RefreshToken`
  - Luu refresh token dang hash de co the rotate/revoke.
  - Giup dang nhap dai han an toan hon access token ngan han.
- [x] Tao repository cho cac entity chinh
  - Dung de service truy van va luu du lieu theo entity.
  - La lop truy cap database chuan cho backend.
- [x] Tao repository cho OTP kich hoat tai khoan
  - Dung de tim OTP con hop le va revoke/mark used.
  - Can cho activation flow o Phase 2.
- [x] Kiem tra mapping PK dung theo tai lieu
  - Dam bao ten cot id dung nhu database design.
  - Giam loi schema/foreign key tren Postgres/Adminer.
- [x] Kiem tra unique constraint quan trong
  - Dam bao username, employee code, shift name, rule code khong trung.
  - Bao ve tinh nhat quan cua du lieu.
- [x] Kiem tra default value entity
  - Dam bao cac status/flag mac dinh dung khi tao object moi.
  - Giam bug khi service khong set het field.
- [x] Verify backend tests
  - Dung de xac nhan entity/repository mapping khong pha app context.
  - La baseline truoc khi them auth/API.

## Phase 2 - Auth & Security

Muc dich: xay lop bao mat va vong doi tai khoan. Phase nay dam bao chi user hop le moi vao he thong, nhan vien moi phai kich hoat OTP va doi mat khau, token co the rotate/revoke.

Ket qua mong doi: login/logout/refresh/me hoat dong, manager tao/reset account nhan vien, cac API con lai duoc bao ve bang Bearer token va role.

- [x] Them dependency JWT
  - Dung de tao va verify access token stateless.
  - Cho phep frontend goi API protected ma backend khong can session server-side.
- [x] Them dependency gui mail neu can
  - Dung de gui OTP kich hoat tai khoan qua email.
  - Can cho flow account do manager tao.
- [x] Them config JWT secret, access token TTL, refresh token TTL
  - Dung de quan ly thoi gian song token va secret theo moi truong.
  - Giam rui ro hard-code secret trong code.
- [x] Them config SMTP/mail sender cho moi truong dev/prod
  - Dung de gui mail qua Gmail/SMTP bang bien moi truong.
  - Tach config nhay cam khoi source code.
- [x] Bo public self-registration cua nhan vien
  - Dung de nhan vien khong tu tao account ngoai quy trinh quan ly.
  - Dam bao du lieu nhan su do manager/admin kiem soat.
- [x] Implement login bang username/password
  - Dung de user nhap credential va nhan token neu hop le.
  - La cua vao chinh cua he thong.
- [x] Neu account chua kich hoat, login tra ve trang thai can xac thuc OTP
  - Dung de frontend dieu huong sang man hinh nhap OTP.
  - Khong cap token cho account chua kich hoat.
- [x] Neu account bi khoa, login bi tu choi
  - Dung de manager/admin vo hieu hoa account khi can.
  - Ngan user bi khoa tiep tuc vao he thong.
- [x] Implement gui OTP kich hoat tai khoan qua email
  - Dung de nhan vien xac minh email/tai khoan sau khi duoc tao.
  - Tang an toan truoc khi cap quyen login.
- [x] Implement verify OTP kich hoat tai khoan
  - Dung de chuyen account tu pending activation sang can doi mat khau.
  - Dam bao OTP dung, con han va chua bi dung lai.
- [x] Implement resend OTP kich hoat tai khoan
  - Dung khi nhan vien khong nhan duoc OTP hoac OTP het han.
  - Van giu account trong trang thai pending cho den khi verify thanh cong.
- [x] Implement bat buoc doi mat khau lan dau sau khi OTP hop le
  - Dung de khong cho nhan vien su dung password mac dinh lau dai.
  - Giam rui ro lo password mac dinh.
- [x] Implement bat buoc doi mat khau sau khi quan ly reset mat khau
  - Dung khi nhan vien quen mat khau va manager reset ve password tam.
  - Buoc user doi password ca nhan truoc khi dung tiep.
- [x] Hash password bang BCrypt
  - Dung de khong luu plain password trong database.
  - Bao ve user neu database bi lo.
- [x] Generate access token JWT
  - Dung de frontend gui Bearer token cho API protected.
  - Access token ngan han giam rui ro khi bi lo.
- [x] Luu refresh token hash vao DB
  - Dung de cap access token moi ma khong bat login lai lien tuc.
  - Luu hash giup token that khong bi lo tu DB.
- [x] Set refresh token vao `HttpOnly Cookie`
  - Dung de trinh duyet gui refresh token an toan hon localStorage.
  - Giam rui ro XSS doc refresh token.
- [x] Implement refresh token rotation
  - Dung de refresh token cu bi thay the moi lan refresh.
  - Giam rui ro replay token cu.
- [x] Implement revoke refresh token khi logout
  - Dung de logout that su vo hieu hoa phien dang nhap.
  - Khong chi xoa token tren frontend.
- [x] Implement `/api/auth/me`
  - Dung de frontend lay thong tin user hien tai sau khi refresh/reload.
  - Ho tro route guard va role guard.
- [x] Chan inactive account login/refresh/me
  - Dung de account bi khoa khong the tao phien moi hoac xem profile.
  - Bao ve ca refresh token con ton tai.
- [x] Chan account chua kich hoat refresh/me
  - Dung de account pending/force change khong truy cap API binh thuong.
  - Dam bao lifecycle duoc enforce o moi entry point.
- [x] Revoke refresh token cu khi account bi reset mat khau
  - Dung de cac phien cu mat hieu luc sau khi manager reset.
  - Giam rui ro user/ke khac tiep tuc dung token cu.
- [x] Cau hinh Spring Security stateless
  - Dung de API khong phu thuoc HTTP session.
  - Phu hop voi JWT va frontend SPA.
- [x] Public `/api/auth/login`, `/api/auth/activation/verify-otp`, `/api/auth/password/change-required`, `/api/auth/refresh`, `/api/auth/logout`, `/api/health`
  - Dung de user thuc hien cac buoc auth can thiet truoc khi co token.
  - Cac endpoint khac van phai authenticated.
- [x] Manager-only `/api/manager/accounts`, `/api/manager/accounts/{accountId}/activation/resend`, `/api/manager/accounts/{accountId}/password/reset`
  - Dung de manager/admin quan ly tai khoan nhan vien.
  - Nhan vien khong duoc tu tao/reset account.
- [x] Protect cac API con lai bang Bearer token
  - Dung de moi API nghiep vu yeu cau user hop le.
  - La nen tang cho phan quyen theo role.
- [x] Viet test `AuthService`
  - Kiem tra business logic login/refresh/me/change password.
  - Giam regression khi sua lifecycle tai khoan.
- [x] Viet test `AuthController`
  - Kiem tra route, cookie, status code va security config.
  - Dam bao frontend nhan response dung.
- [x] Viet test OTP activation flow
  - Kiem tra tao/verify/resend OTP.
  - Dam bao account activation khong bi bypass.
- [x] Viet test force change password flow
  - Kiem tra account can doi password khong duoc vao API binh thuong.
  - Dam bao password moi duoc hash va token moi duoc cap dung luc.
- [x] Push auth work len GitHub `main`
  - Dung de dua nen auth vao base branch cho cac phase sau.
  - Dam bao team/PR tiep theo cung dung chung nen bao mat.

## Phase 3 - Admin Core

Muc dich: cung cap bo API quan tri cot loi cho manager/admin de quan ly nhan vien, tai khoan, loai ca va quy tac lap lich. Phase nay la nen tang cho frontend dashboard va cac phase lap lich sau.

Ket qua mong doi: manager/admin co the quan ly du lieu dau vao quan trong truoc khi tao lich: nhan vien, account, shift type, scheduling rules.

- [x] API danh sach nhan vien
  - Dung de manager xem toan bo nhan vien trong he thong.
  - Ho tro man hinh quan ly nhan vien va chon nhan vien khi lap lich.
- [x] API them nhan vien kem tao account bang email
  - Dung de manager tao nhan vien moi va tai khoan dang nhap tu mot luong quan ly.
  - Dam bao nhan vien moi bat dau o trang thai can kich hoat OTP.
- [x] API cap nhat nhan vien
  - Dung de sua thong tin ho so nhu ten, email, phone, status.
  - Giup du lieu lien he va trang thai nhan su luon dung.
- [x] API khoa/mo khoa nhan vien
  - Dung de tam ngung hoac kich hoat lai nhan vien trong nghiep vu.
  - Khi khoa nhan vien, account lien quan cung bi vo hieu hoa va token bi revoke.
- [x] API tim kiem nhan vien
  - Dung de loc nhanh theo ma, ten, status.
  - Can cho UI quan ly khi danh sach nhan vien dai.
- [x] API danh sach tai khoan
  - Dung de manager/admin xem account, role, status, active flag va employee lien ket.
  - Ho tro loc/tim account dang pending, can doi mat khau, bi khoa hoac active.
- [x] API quan ly tao account nhan vien voi email
  - Dung de tao account cho nhan vien thong qua manager/admin, khong cho public self-register.
  - Tao OTP va gui email kich hoat.
- [x] API gui lai OTP kich hoat cho nhan vien
  - Dung khi nhan vien chua kich hoat va can OTP moi.
  - Giu quy trinh kich hoat an toan thay vi reset truc tiep thanh active.
- [x] API reset mat khau tam cho nhan vien
  - Dung khi nhan vien quen mat khau va lien he quan ly.
  - Dat account ve trang thai can doi password de tranh dung password tam lau dai.
- [x] API set account ve trang thai can kich hoat/doi mat khau lai sau reset
  - Dung de enforce lifecycle sau khi OTP/reset.
  - Dam bao account khong bypass activation/password-change.
- [x] API revoke refresh tokens cua nhan vien sau reset/khoa account
  - Dung de vo hieu hoa phien dang nhap cu ngay lap tuc.
  - Giam rui ro token cu tiep tuc goi API.
- [x] API khoa account nhan vien
  - Dung de khoa rieng tai khoan nhan vien ma khong can sua ho so employee.
  - Chi ap dung cho account role `EMPLOYEE`, tranh manager/admin khoa nham tai khoan dac quyen.
- [x] API mo khoa account nhan vien
  - Dung de mo lai account nhan vien da bi khoa.
  - Giu lifecycle status nhu `PENDING_ACTIVATION` hoac `FORCE_PASSWORD_CHANGE` neu account chua hoan tat quy trinh.
- [x] API quan ly loai ca truc
  - Dung de xem danh sach cau hinh ca truc hien co.
  - La du lieu dau vao cho work shifts, manual scheduling va AI scheduling.
- [x] API them loai ca
  - Dung de tao ca moi nhu ngoai gio, thong tam, truc.
  - Cho phep manager cau hinh gio bat dau/ket thuc, duration, min/max staff.
- [x] API cap nhat loai ca
  - Dung de sua thong tin ca khi quy dinh van hanh thay doi.
  - Anh huong cac work shifts/AI sau nay nen can validate ky.
- [x] API khoa/mo khoa loai ca
  - Dung de tam ngung loai ca khong con su dung ma khong xoa du lieu cu.
  - Giu lich su assignment va preference lien quan.
- [x] API quan ly scheduling rules
  - Dung de xem/sua bo quy tac lap lich hard/soft.
  - La cau noi giua nghiep vu va AI/manual scheduler.
- [x] API cau hinh dinh muc ca/tuan
  - Dung de dat quota nhu so ca toi da/toi thieu moi tuan, loai ca dac biet.
  - AI dung de can bang tai va tuan thu quy dinh.
- [x] API cau hinh trong so soft constraints
  - Dung de dieu chinh muc uu tien fairness, preference, weekend, heavy shift.
  - Giup manager tune ket qua AI ma khong sua code.
- [x] Test admin employee APIs
  - Kiem tra list/search/detail/update/lock/unlock employee.
  - Dam bao role guard va service logic khong regression.
- [x] Test admin account creation APIs
  - Kiem tra manager/admin tao account nhan vien, role guard, duplicate validation.
  - Dam bao account tao ra dung lifecycle pending activation.
- [x] Test admin password reset/reactivation APIs
  - Kiem tra resend OTP, reset password va revoke token.
  - Dam bao reset khong tao lo hong auth.
- [x] Test shift type APIs
  - Kiem tra create/update/list/lock/unlock loai ca.
  - Dam bao cau hinh ca truc dung truoc khi scheduling dung.
- [x] Test scheduling rule APIs
  - Kiem tra doc/sua rule values va weights.
  - Dam bao AI/manual scheduler nhan cau hinh hop le.

## Phase 4 - Employee Core

Muc dich: cho nhan vien tu thao tac voi lich cua minh: xem profile, xem lich, xin nghi, dang ky nguyen vong va doi ca. Phase nay bien he thong tu cong cu quan ly mot chieu thanh workflow hai chieu giua nhan vien va quan ly.

Ket qua mong doi: nhan vien co the tu gui nhu cau/lien quan lich; manager va AI co du input thuc te de lap lich hop ly.

- [x] API nhan vien xem profile ca nhan (da xong: dung GET /api/auth/me)
  - Dung de nhan vien xem thong tin account/employee cua minh.
  - Ho tro man hinh ca nhan tren frontend.
- [x] API nhan vien xem lich cua minh (da xong: employee sees own PUBLISHED schedule)
  - Dung de nhan vien biet ca duoc phan cong.
  - La chuc nang cot loi sau khi lich duoc tao/cong bo.
- [x] API dang ky ngay nghi
  - Dung de nhan vien gui don xin nghi theo ngay/khoang ngay.
  - Don duoc manager duyet va AI khong phan ca vao ngay nghi da duyet.
- [x] API xem danh sach ngay nghi da gui
  - Dung de nhan vien theo doi trang thai pending/approved/rejected.
  - Giam viec hoi quan ly thu cong.
- [x] API huy yeu cau nghi neu con pending
  - Dung de nhan vien thu hoi don truoc khi quan ly xu ly.
  - Giu du lieu sach, khong can manager tu choi don sai.
- [x] API manager xem/duyet leave requests
  - Dung de manager xem danh sach, duyet hoac tu choi don nghi cua nhan vien.
  - Dam bao don nghi duoc xu ly truoc khi scheduling dung lam input.
- [x] API dang ky nguyen vong muon lam/khong muon lam
  - Dung de nhan vien gui preference theo ngay/loai ca.
  - AI dung nhu soft constraint de lich cong bang va hop ly hon.
- [x] API xem danh sach nguyen vong
  - Dung de nhan vien xem cac preference da gui.
  - Ho tro sua/huy khi con pending.
- [x] API cap nhat nguyen vong pending
  - Dung de sua preference truoc khi lich duoc tao/duyet.
  - Giam data sai dua vao AI.
- [x] API gui yeu cau doi ca (da xong: POST /api/employee/swap-requests + target respond + manager approve/reject, status-only)
  - Dung de nhan vien de xuat doi assignment voi nguoi khac.
  - Ho tro workflow doi ca co phe duyet.
- [x] API xem yeu cau doi ca lien quan den minh (da xong: GET /api/employee/swap-requests + GET /api/admin/swap-requests)
  - Dung de nhan vien xem request do minh tao hoac duoc yeu cau.
  - La nen tang cho UI xu ly doi ca.
- [x] Test leave request flow (da xong: EmployeeLeaveRequest*Test pass trong full suite)
  - Kiem tra tao/xem/huy don nghi va status.
  - Dam bao ngay nghi sau nay dung trong scheduling.
- [x] Test preference flow (da xong: EmployeePreference*Test pass trong full suite)
  - Kiem tra tao/xem/sua preference.
  - Dam bao soft constraint input hop le.
- [x] Test swap request flow (da xong: EmployeeSwapRequest*Test + AdminSwapRequest*Test)
  - Kiem tra tao/xem yeu cau doi ca.
  - Dam bao workflow doi ca khong lam hong assignment.

## Phase 5 - Scheduling Manual

Muc dich: cho manager tao ky lap lich, tao ca lam va phan cong thu cong. Phase nay can co truoc AI de he thong van dung duoc khi AI chua hoan thien hoac khi can override thu cong.

Ket qua mong doi: manager co the tao lich, gan nhan vien, kiem tra xung dot, duyet va cong bo lich.

- [x] API tao ky lap lich `SchedulePeriod`
  - Dung de tao khoang ngay can lap lich, vi du tuan/thang.
  - Gom cac work shifts va assignments vao cung mot dot quan ly.
- [x] API tao work shifts theo ky
  - Dung de sinh cac ca lam cu the dua tren shift types va ngay trong ky.
  - Tao danh sach slot can phan cong nhan vien.
- [x] API xem lich theo ky (da xong: period schedule view kem assignments; ho tro moi status, khong gioi han DRAFT)
  - Dung de manager xem toan bo lich trong mot period.
  - Ho tro man hinh calendar/table tong quan.
- [x] API xem lich theo nhan vien (da xong: manager views any employee schedule)
  - Dung de xem workload cua tung nhan vien.
  - Ho tro kiem tra qua tai/thieu ca.
- [x] API phan cong nhan vien vao ca thu cong (da xong: assign + delete assignment; chi cho phep khi period o DRAFT)
  - Dung de manager gan employee vao work shift.
  - La fallback/override khi AI khong dung hoac can dieu chinh.
- [x] API cap nhat phan cong (da xong: manager can update assignment employee in DRAFT with manual assignment validations)
  - Dung de doi nhan vien/ca trong assignment.
  - Can validate xung dot va gio lam.
- [x] API khoa phan cong `is_locked` (da xong; chi cho phep khi period o DRAFT)
  - Dung de danh dau assignment khong duoc AI thay doi.
  - Quan trong khi manager muon giu cac ca da chot.
- [x] API mo khoa phan cong (da xong; chi cho phep khi period o DRAFT)
  - Dung de cho phep AI/manual adjustment sua lai assignment truoc do.
  - Tang linh hoat khi lich thay doi.
- [x] API kiem tra trung ca khi phan cong thu cong (da xong nhu mot phan cua manual assign: chan overlap)
  - Dung de ngan mot nhan vien bi gan hai ca bi overlap.
  - Bao ve tinh hop le cua lich.
- [x] API kiem tra gioi han gio lam/ngay (da xong: manual assignment rejects employees exceeding `MAX_WORK_HOURS_PER_DAY` on the work date)
  - Dung de ngan assignment vuot quy dinh/nghiep vu.
  - Giam rui ro lich khong kha thi.
- [x] API duyet lich (da xong: approve DRAFT -> APPROVED kem staffing validation)
  - Dung de manager xac nhan lich da san sang.
  - La buoc truoc khi cong bo cho nhan vien.
- [x] API cong bo lich (da xong: publish APPROVED -> PUBLISHED; cancel DRAFT/APPROVED -> CANCELLED)
  - Dung de chuyen lich sang trang thai nhan vien co the xem.
  - Co the kich hoat notification o Phase 7.
- [ ] Test tao ky lap lich
  - Kiem tra tao period va validate range ngay.
  - Dam bao lich co container hop le.
- [ ] Test phan cong thu cong
  - Kiem tra assignment, update, lock/unlock va conflict validation.
  - Dam bao manual scheduling tin cay.
- [ ] Test duyet/cong bo lich
  - Kiem tra state transition cua schedule period.
  - Dam bao lich chi cong bo khi hop le.

## Phase 6 - AI Scheduling

Muc dich: tu dong tao lich dua tren nhan vien active, loai ca, don nghi, preference, assignment bi khoa va scheduling rules. Phase nay bien he thong thanh cong cu toi uu lich thay vi chi CRUD/manual.

Ket qua mong doi: backend goi AI service, AI tra lich toi uu hoac ly do infeasible, backend luu assignments va optimization run.

Backend/database compatibility notes from rule foundation slices:

- Phase 6 quota semantics do not require a database schema change. Backend/API field names stay `overtimeShiftsPerWeek` and `bathingShiftsPerWeek`, and DB rule codes stay `OVERTIME_SHIFTS_PER_WEEK` and `BATHING_SHIFTS_PER_WEEK`; their business meaning for AI is max distinct work dates per employee per ISO week.
- Backend-to-AI payload mapping must keep the existing `quotaCategory` contract: backend `REGULAR` maps to AI `null`; backend `OVERTIME`, `BATHING`, and `ON_CALL` map directly.
- `shiftCode` and `penaltyWeightMultiplier` are persisted on backend shift types, but AI schema `1.0` does not consume `penaltyWeightMultiplier` yet. A later slice should explicitly decide whether/how to send it.
- `WorkShift.startsAt` and `WorkShift.endsAt` are persisted exact windows. The backend AI payload slice should send these persisted values; if old rows can have null windows, add a backfill or derive fallback before calling AI.
- Production-hardening follow-up: after existing shift types are backfilled with stable `shiftCode`, enforce `shift_code` as not null if the deployment needs DB-level protection.

- [x] Thiet ke request payload tu backend sang AI service
  - Dung de thong nhat schema du lieu dau vao cho solver.
  - Giam coupling giua Spring Boot backend va AI service.
- [x] API backend goi AI service tao lich
  - Dung de manager kick off auto scheduling tu backend.
  - Backend gom du lieu, gui AI service, nhan ket qua va luu DB.
- [x] AI service nhan danh sach nhan vien active
  - Dung de chi lap lich cho nhan vien dang lam viec va account hop le.
  - Tranh gan ca cho nhan vien inactive.
- [x] AI service nhan shift types active
  - Dung de biet cac loai ca hop le va min/max staff.
  - Loai ca inactive khong duoc dung de tao lich moi.
- [x] AI service nhan approved leave requests
  - Dung de hard-block ngay nhan vien da nghi duoc duyet.
  - Giam conflict giua nghi phep va assignment.
- [x] AI service nhan preferences
  - Dung de toi uu theo nguyen vong nhan vien neu co the.
  - Thuong la soft constraint vi khong phai luc nao cung dap ung duoc.
- [x] AI service nhan locked assignments
  - Dung de giu cac phan cong manager da chot.
  - AI chi lap phan con lai xung quanh cac assignment bi khoa.
- [x] Implement hard constraint: du nhan su moi ca
  - Dung de moi ca co so nguoi toi thieu/toi da dung cau hinh.
  - La dieu kien bat buoc de lich kha thi.
- [x] Implement hard constraint: khong trung ca
  - Dung de mot nhan vien khong lam hai ca overlap.
  - Bao ve tinh hop ly cua lich.
- [x] Implement hard constraint: nghi toi thieu 12 gio
  - Dung de dam bao khoang nghi giua hai ca.
  - Giam lich gay qua tai/sai quy dinh.
- [x] Implement hard constraint: toi da 12 gio/ngay
  - Dung de gioi han tong thoi gian lam trong ngay.
  - Tranh gan lich vuot suc/khong hop le.
- [x] Implement hard constraint: khong phan ca vao ngay nghi da duyet
  - Dung de ton trong leave requests approved.
  - La rang buoc bat buoc tu nghiep vu.
- [x] Implement hard constraint: giu phan cong bi khoa
  - Dung de AI khong thay doi assignment manager da lock.
  - Cho phep ket hop manual va auto scheduling.
- [x] Implement quota: ngoai gio 1 ngay/tuan
  - Dung de gioi han tan suat ca ngoai gio theo tuan.
  - Ho tro quy dinh noi bo cua phong/bo phan.
- [x] Implement quota: thong tam 2 ngay/tuan
  - Dung de gioi han so ngay thong tam neu ap dung.
  - Giam lich khong cong bang hoac qua tai.
  - Quota semantics slice: AI counts `OVERTIME` and `BATHING` by distinct work dates per employee per ISO week while keeping legacy schema field names.
- [x] Implement quota: truc 1 ngay/6 ngay neu ap dung
  - Dung de can bang ca truc dac biet theo chu ky.
  - Bao dam phan bo cong bang hon.
- [x] Implement soft constraint: fairness penalty
  - Dung de can bang so ca/gio lam giua nhan vien.
  - Neu khong the tuyet doi cong bang, AI toi thieu hoa chenh lech.
- [x] Implement soft constraint: consecutive heavy shift penalty
  - Dung de tranh xep nhieu ca nang lien tiep.
  - Giam met moi va lich xau.
- [x] Implement soft constraint: weekend penalty
  - Dung de phan bo ca cuoi tuan cong bang.
  - Giam viec mot nhan vien bi gan cuoi tuan qua nhieu.
- [x] Implement soft constraint: preference penalty
  - Dung de phat khi lich khong thoa nguyen vong.
  - Giup AI uu tien trai nghiem nhan vien.
- [x] Implement soft constraint: work hour imbalance
  - Dung de phat chenh lech tong gio lam.
  - Ho tro cong bang theo workload.
- [x] Tra ket qua lich toi uu ve backend
  - Dung de backend biet assignment nao can luu va penalty bao nhieu.
  - Ket qua phai co cau truc de debug/demo.
- [x] Backend luu `ShiftAssignment`
  - Dung de bien ket qua AI thanh lich that trong database.
  - Frontend/employee APIs doc tu bang nay.
- [x] Backend luu `OptimizationRun`
  - Dung de ghi lai lan chay AI, status, penalty, error.
  - Ho tro audit va so sanh ket qua.
- [x] Backend luu `total_penalty`
  - Dung de biet chat luong lich toi uu.
  - Co the hien tren UI/report.
- [x] Xu ly INFEASIBLE va tra ly do
  - Dung khi khong co lich thoa hard constraints.
  - Manager can ly do de sua input: them nhan vien, giam ca, sua nghi phep/rules.
- [x] Test AI service bang pytest
  - Kiem tra solver logic doc lap voi backend.
  - Nhanh hon integration test khi tune constraint.
- [x] Test backend integration voi mocked AI service
  - Kiem tra backend gom payload, goi AI, luu ket qua.
  - Khong phu thuoc AI service that trong unit/integration tests.

## Phase 7 - Notification & Logs

Muc dich: tao thong bao cho user va ghi audit log cho hanh dong quan trong. Phase nay giup he thong minh bach, de truy vet, va user khong bo lo thay doi lich/don.

Ket qua mong doi: manager xem duoc activity logs, user xem/danh dau notification, cac event quan trong duoc ghi lai.

- [x] Tao service ghi `ActivityLog`
  - Dung de cac service khac goi chung khi co hanh dong quan trong.
  - Giam duplicate code log trong tung module.
- [x] Ghi log khi tao/sua/xoa nhan vien
  - Dung de audit thay doi du lieu nhan su.
  - Quan trong khi co tranh chap ai sua thong tin.
- [x] Ghi log khi duyet nghi
  - Dung de biet ai duyet/tu choi don nghi va luc nao.
  - Ho tro giai thich vi sao AI khong phan ca ngay do.
- [x] Ghi log khi tao lich tu dong
  - Dung de trace lan chay AI va nguoi khoi tao.
  - Ho tro debug neu lich ket qua co van de.
- [x] Ghi log khi dieu chinh lich
  - Dung de audit manual adjustment sau khi AI/manual schedule.
  - Giu lich su thay doi assignment.
- [x] Ghi log khi cong bo lich
  - Dung de biet khi nao lich chinh thuc cho nhan vien.
  - Co the lien ket voi notification.
- [x] Ghi log khi xu ly doi ca
  - Dung de trace phe duyet/tu choi swap request.
  - Giam tranh chap ve thay doi ca.
- [x] Tao notification khi lich duoc cong bo
  - Dung de bao nhan vien co lich moi.
  - Tang kha nang user xem lich kip thoi.
- [x] Tao notification khi don nghi duoc duyet/tu choi
  - Dung de nhan vien biet ket qua leave request.
  - Giam can trao doi ngoai he thong.
- [x] Tao notification khi co yeu cau doi ca
  - Dung de nhan vien duoc yeu cau doi ca biet va phan hoi.
  - Ho tro workflow doi ca hai phia.
- [x] Tao notification khi yeu cau doi ca duoc duyet/tu choi
  - Dung de cac ben lien quan biet ket qua.
  - Dam bao lich ca nhan cap nhat dung ky vong.
- [x] API xem danh sach notification
  - Dung de frontend hien inbox/thong bao.
  - User co noi xem cac event can chu y.
- [x] API danh dau notification da doc
  - Dung de user quan ly thong bao da xem.
  - Ho tro badge unread count.
- [x] API xem activity logs cho quan ly
  - Dung de manager/admin xem audit trail.
  - Can cho demo tinh minh bach cua he thong.
- [x] Test notification service
  - Kiem tra tao/xem/mark read notification.
  - Dam bao user khong thay notification cua nguoi khac.
- [x] Test activity log service
  - Kiem tra log dung actor/action/entity.
  - Dam bao audit khong bi mat khi thao tac quan trong.

## Phase 8 - Export & Report

Muc dich: cho phep xuat lich va bao cao ra file de su dung ngoai he thong, nop/demo, hoac in an. Phase nay tang gia tri thuc te vi quan ly thuong can Excel/PDF.

Ket qua mong doi: manager co nut export va cac bao cao co filter ro rang.

- [ ] API xuat lich Excel
  - Dung de tai lich theo ky ra file `.xlsx`.
  - Phu hop in an/chia se voi nguoi khong dung he thong.
- [ ] API xuat lich PDF neu can
  - Dung de tao ban lich co dinh de in/gui.
  - Huu ich cho demo va bao cao chinh thuc.
- [ ] Export lich theo ky
  - Dung de lay toan bo lich cua mot schedule period.
  - La format tong quan cho manager.
- [ ] Export lich theo nhan vien
  - Dung de lay lich ca nhan cua mot hoac nhieu nhan vien.
  - Huu ich khi gui lich rieng cho tung nguoi.
- [ ] Export bao cao tong so ca moi nhan vien
  - Dung de thong ke workload theo so ca.
  - Ho tro danh gia cong bang.
- [ ] Export bao cao so gio lam moi nhan vien
  - Dung de thong ke duration/tong gio.
  - Co the dung cho doi chieu cong/OT.
- [ ] Export bao cao penalty/optimization result
  - Dung de giai thich chat luong lich AI.
  - Cho biet lich co vi pham soft constraint nao nhieu.
- [ ] UI nut export Excel/PDF
  - Dung de manager thao tac export tu frontend.
  - Khong can goi API thu cong.
- [ ] Test export Excel
  - Kiem tra file tao duoc, co sheet/cot/data dung.
  - Dam bao export khong bi hong format.
- [ ] Test export du lieu dung filter
  - Kiem tra export theo period/employee/status dung.
  - Tranh xuat nham du lieu ngoai filter.

## Phase 9 - Frontend Integration

Muc dich: xay UI hoan chinh ket noi backend cho manager va employee. Phase nay bien API thanh san pham co the demo va nguoi dung thao tac.

Ket qua mong doi: user co luong dang nhap/kich hoat, dashboard, quan ly nhan vien/ca/lich, va employee self-service.

Frontend backend-contract alignment notes:

- Frontend hien la mock-first: services doc seed data tu `src/mocks` va dung UI-friendly model. Khi ket noi backend that, khong nen sua backend theo mock cu; backend API/DTO la source of truth.
- Can them adapter layer `backend DTO -> frontend UI model` de page/component khong tu map response raw. Mock data cung phai mo phong backend DTO hoac di qua cung adapter de tranh mock pass nhung backend fail.
- Can co config bat/tat data source, vi du `VITE_USE_MOCK_API`, de cung service public API co the chay voi mock hoac backend.
- UI terminology phai theo nghiep vu moi: `overtimeShiftsPerWeek` va `bathingShiftsPerWeek` la so ngay distinct moi tuan, khong phai so ca/shift rows.
- Giao dien khong can redesign lon trong slice integration. Uu tien chinh form, label, table columns, loading/error/empty states va calendar/detail hien dung du lieu backend.

- [ ] Tao frontend HTTP client va API mode config
  - Dung `VITE_API_BASE_URL` de goi backend that.
  - Dung `VITE_USE_MOCK_API` de chuyen giua mock va backend trong qua trinh migrate.
  - Chuan hoa parse loi backend thanh message hien thi duoc tren UI.
- [ ] Tao backend DTO types va adapter functions trong frontend
  - Dinh nghia type raw theo backend DTO cho employee, shift type, scheduling rules, schedule period, work shift, assignment.
  - Adapter map raw DTO sang UI model hien tai de component it bi thay doi.
  - Khong de page/component map response raw truc tiep.
- [ ] Cap nhat mock data theo backend contract
  - Mock nen mo phong response backend DTO, khong tao shape rieng qua xa backend.
  - Mock va backend cung di qua adapter layer.
  - Giam rui ro UI chay mock duoc nhung fail khi doi sang backend.
- [ ] Align giao dien quan ly nhan vien voi backend API
  - Endpoint chinh: `/api/admin/employees`.
  - Xu ly khac biet `id` backend numeric va UI string neu can.
  - Hien/luu dung status, employee code, contact va account summary backend tra ve.
- [ ] Align giao dien quan ly loai ca voi backend API
  - Endpoint chinh: `/api/admin/shift-types`.
  - Hien va submit dung `shiftCode`, `shiftName`, `startTime`, `endTime`, `durationHours`, `overnight`, `heavy`, `minStaff`, `maxStaff`, `quotaCategory`, `applicableWeekdays`, `appliesOnHolidays`, `penaltyWeightMultiplier`, `status`.
  - Map label tieng Viet cho quota category: `REGULAR`, `OVERTIME`, `BATHING`, `ON_CALL`.
  - Khong dung `shiftDefinitions` lam source chinh khi backend da co shift type data.
- [ ] Them/cap nhat giao dien quy dinh xep lich
  - Endpoint chinh: `/api/admin/scheduling-rules`.
  - Chia UI thanh hard rules, quotas va soft weights.
  - Label quota dung nghia ngay/tuan: ngay ngoai gio toi da, ngay thong tam toi da, khoang cach ngay truc.
- [ ] Align giao dien ky lich va work shifts
  - Endpoints chinh: `/api/admin/schedule-periods`, `/api/admin/schedule-periods/{periodId}/work-shifts`, `/api/admin/work-shifts/{workShiftId}`.
  - Calendar/table hien theo `workDate`, detail/tooltip hien `startsAt`, `endsAt`, `workMinutes`.
  - Hien badge ca qua ngay khi `isTransitionShift = true`.
- [ ] Align giao dien assignment va dieu chinh lich thu cong
  - Endpoints chinh: tao assignment theo work shift, xem schedule theo period/employee, delete assignment, lock/unlock assignment.
  - Hien locked assignment bang badge/icon ro rang.
  - Group assignment theo work shift de lich nhieu nhan vien cung ca khong bi roi.
- [ ] Cap nhat giao dien tao lich tu dong
  - Truoc khi chay, hien checklist input: period, work shifts, active employees, active shift types, scheduling rules, locked assignments.
  - Sau khi chay, hien `SUCCESS`, `INFEASIBLE`, `FAILED`, total penalty va diagnostics neu co.
  - Khong hien thong bao thanh cong/that bai chung chung khi backend/AI co ly do cu the.
- [ ] Cap nhat giao dien ngay nghi va nguyen vong
  - Ngay nghi approved phai ghi ro AI se khong xep ca trong khoang ngay nay.
  - Nguyen vong phai ghi ro la soft constraint, khong dam bao luon duoc dap ung.
- [ ] Them frontend contract/adapter tests toi thieu
  - Test sample backend DTO map dung sang UI model cho employee, shift type, work shift va scheduling rules.
  - Test service chon mock/backend theo config.
  - Chay `npm run build` de bat loi type khi contract doi.

- [ ] Setup routing
  - Dung de chia trang login, dashboard, admin, employee.
  - La nen cho route guard va role guard.
- [ ] Tao layout chung
  - Dung de co header/sidebar/container nhat quan.
  - Giam duplicate UI tren cac trang.
- [ ] Tao trang login
  - Dung de user nhap username/password va nhan access token.
  - Dieu huong theo lifecycle: OTP, change password, dashboard.
- [ ] Bo trang register public cho nhan vien
  - Dung de UI khong mo self-registration.
  - Dong bo voi backend chi manager tao account.
- [ ] Tao trang nhap OTP kich hoat tai khoan
  - Dung de nhan vien kich hoat account sau khi nhan email.
  - La buoc sau login pending activation.
- [ ] Tao trang resend OTP kich hoat
  - Dung khi OTP mat/het han.
  - Goi endpoint manager resend neu flow frontend cho phep theo role, hoac hien huong dan lien he quan ly.
- [ ] Tao trang doi mat khau bat buoc lan dau
  - Dung de user doi password sau OTP/reset.
  - Chi sau khi doi thanh cong moi vao dashboard.
- [ ] Tao trang thong bao quen mat khau: lien he quan ly de cap lai
  - Dung vi he thong khong co self-service forgot password public.
  - Giam nham lan cho nhan vien.
- [ ] Luu access token phia frontend
  - Dung de attach Bearer token vao API protected.
  - Can quan ly memory/local storage theo quy uoc bao mat.
- [ ] Goi refresh token bang cookie
  - Dung de cap access token moi khi het han.
  - Refresh token nam trong HttpOnly cookie nen JS khong doc truc tiep.
- [ ] Axios interceptor attach Bearer token
  - Dung de moi request protected tu dong co Authorization header.
  - Giam code lap lai o tung API call.
- [ ] Axios interceptor tu refresh khi access token expired
  - Dung de user khong bi logout moi lan access token het han.
  - Neu refresh fail thi quay ve login.
- [ ] Route guard cho protected pages
  - Dung de chan user chua login truy cap dashboard/admin.
  - Tang an toan va UX.
- [ ] Role guard cho manager pages
  - Dung de nhan vien khong vao trang quan ly.
  - Dong bo voi backend role guard.
- [ ] Trang dashboard quan ly
  - Dung de manager xem tong quan: nhan vien, lich, task can xu ly.
  - La landing page sau login manager/admin.
- [ ] Trang quan ly nhan vien
  - Dung de list/search/detail/update/lock/unlock nhan vien.
  - Ket noi Phase 3 employee APIs.
- [ ] Form quan ly tao nhan vien kem email/account
  - Dung de manager tao nhan vien moi va gui OTP.
  - Ket noi endpoint create account.
- [ ] Form quan ly reset mat khau tam cho nhan vien
  - Dung de manager reset khi nhan vien quen password.
  - Sau reset, nhan vien phai doi password.
- [ ] Nut quan ly gui lai OTP kich hoat
  - Dung de resend OTP cho account pending activation.
  - Ho tro xu ly van de email/OTP het han.
- [ ] Nut quan ly khoa/mo khoa account
  - Dung de vo hieu hoa hoac kich hoat lai account nhan vien.
  - Ket noi admin account APIs.
- [ ] Trang quan ly loai ca
  - Dung de xem/them/sua/khoa loai ca.
  - La cau hinh dau vao cho scheduling.
- [ ] Trang quan ly ngay nghi
  - Dung de manager xem/duyet leave requests.
  - Anh huong truc tiep den scheduling.
- [ ] Trang quan ly nguyen vong
  - Dung de xem preference cua nhan vien.
  - Ho tro giai thich input cho AI.
- [ ] Trang tao lich tu dong
  - Dung de manager chon period va chay AI scheduling.
  - Hien ket qua/toi uu/infeasible reason.
- [ ] Trang xem lich dang calendar/table
  - Dung de xem lich theo ky/nhan vien.
  - La man hinh chinh sau khi co assignments.
- [ ] Trang dieu chinh lich thu cong
  - Dung de manager sua assignment, lock/unlock ca.
  - Ho tro override AI/manual scheduling.
- [ ] Trang duyet va cong bo lich
  - Dung de chot lich truoc khi nhan vien thay.
  - Kich hoat notification o Phase 7.
- [ ] Trang xu ly doi ca
  - Dung de manager/nhan vien xem va xu ly swap requests.
  - Ho tro workflow doi ca co phe duyet.
- [ ] Trang thong bao
  - Dung de user xem notification va mark read.
  - Giam phu thuoc email/chat ngoai he thong.
- [ ] Trang activity logs
  - Dung de manager/admin xem audit trail.
  - Huu ich cho demo va debug van hanh.
- [ ] Trang nhan vien xem lich
  - Dung de employee xem ca cua minh.
  - Chuc nang cot loi cho nguoi dung employee.
- [ ] Trang nhan vien dang ky nghi
  - Dung de employee gui leave request.
  - Input quan trong cho scheduling.
- [ ] Trang nhan vien dang ky nguyen vong
  - Dung de employee gui preference.
  - Input cho soft constraints AI.
- [ ] Trang nhan vien gui doi ca
  - Dung de employee tao swap request khi khong the lam ca.
  - Giam viec doi ca ngoai he thong.
- [ ] Responsive desktop/mobile
  - Dung de UI dung duoc tren laptop va dien thoai.
  - Nhan vien co the xem lich/thao tac nhanh tren mobile.
- [ ] Test frontend build
  - Kiem tra TypeScript/build khong loi.
  - La dieu kien truoc khi demo/deploy.

## Phase 10 - Testing & Demo

Muc dich: tao du lieu demo, test flow end-to-end, va chuan bi tai lieu/hinh anh cho nop bai. Phase nay dam bao san pham co the trinh bay tron ven.

Ket qua mong doi: Docker compose chay sach, co account/nhan vien/ca/rules mau, cac luong chinh duoc test, va co huong dan demo.

- [ ] Viet seed data demo
  - Dung de co du lieu mau nhanh khi demo.
  - Giam thoi gian nhap tay moi lan reset DB.
- [ ] Seed manager account
  - Dung de co user quan ly dang nhap ngay sau khi setup.
  - Can cho demo/admin workflows.
- [ ] Seed danh sach nhan vien mau
  - Dung de co du nhan vien lap lich.
  - Giup demo fairness/AI co y nghia.
- [ ] Seed account nhan vien chua kich hoat
  - Dung de demo OTP activation flow.
  - Xac nhan lifecycle pending hoat dong.
- [ ] Seed account nhan vien can doi mat khau
  - Dung de demo force password change sau reset/OTP.
  - Xac nhan UI/backend dieu huong dung.
- [ ] Seed loai ca mau: ngoai gio, thong tam, truc
  - Dung de co cau hinh shift type thuc te.
  - La input cho manual/AI scheduling.
- [ ] Seed scheduling rules mau
  - Dung de AI co hard/soft constraints mac dinh.
  - Giam setup truoc khi demo AI.
- [ ] Test flow quan ly tao account nhan vien
  - Kiem tra tu manager create den email/OTP/lifecycle.
  - Dam bao nhan vien moi vao he thong dung quy trinh.
- [ ] Test flow nhan vien nhan OTP email kich hoat
  - Kiem tra mail config va OTP verify.
  - Quan trong vi day la lan dau nhan vien dung he thong.
- [ ] Test flow nhan vien doi mat khau lan dau
  - Kiem tra password mac dinh khong duoc dung lau dai.
  - Dam bao sau doi password user moi active.
- [ ] Test flow login/refresh/logout
  - Kiem tra phien dang nhap hoat dong va token revoke khi logout.
  - La flow bao mat cot loi.
- [ ] Test flow quen mat khau: nhan vien lien he quan ly
  - Kiem tra quy trinh reset do manager thuc hien.
  - Dong bo voi yeu cau khong co forgot password public.
- [ ] Test flow quan ly reset mat khau va gui lai OTP
  - Kiem tra manager co the xu ly tai khoan bi loi activation/password.
  - Dam bao token cu bi revoke.
- [ ] Test flow quan ly nhan vien
  - Kiem tra CRUD/list/search/lock employee.
  - Dam bao Phase 3 hoat dong trong UI/API.
- [ ] Test flow dang ky nghi
  - Kiem tra employee gui don va manager xu ly.
  - Input phai anh huong scheduling.
- [ ] Test flow dang ky nguyen vong
  - Kiem tra employee gui preference va AI/manual xem duoc.
  - Ho tro demo soft constraints.
- [ ] Test flow tao lich tu dong
  - Kiem tra backend goi AI va luu ket qua.
  - La diem demo quan trong cua de tai.
- [ ] Test flow dieu chinh lich
  - Kiem tra manager sua assignment/lock sau AI.
  - Dam bao manual override hoat dong.
- [ ] Test flow duyet/cong bo lich
  - Kiem tra lich chuyen trang thai va employee xem duoc.
  - La buoc dua lich vao su dung that.
- [ ] Test flow doi ca
  - Kiem tra employee tao swap request va xu ly ket qua.
  - Dam bao lich cap nhat dung sau doi ca.
- [ ] Test flow notification
  - Kiem tra thong bao khi lich/don/doi ca thay doi.
  - Dam bao user khong bo lo su kien.
- [ ] Test export Excel/PDF
  - Kiem tra file xuat dung va mo duoc.
  - Can cho demo/bao cao.
- [ ] Kiem tra Docker compose chay sach
  - Dung de dam bao nguoi cham/demo co the chay project tu dau.
  - Phat hien loi env/migration/service health.
- [ ] Viet huong dan demo
  - Dung de trinh bay theo kich ban ro rang.
  - Giam rui ro quen buoc khi demo.
- [ ] Chuan bi du lieu demo cho giang vien
  - Dung de co case study du nhan vien/ca/nghi/preference.
  - Lam ro gia tri cua AI scheduling.
- [ ] Chup man hinh minh hoa
  - Dung cho bao cao va slide.
  - Chung minh UI/flow da hoan thien.
- [ ] Hoan thien bao cao mo ta bai toan AI
  - Dung de giai thich bai toan toi uu lich.
  - Bao gom input, output, constraint, objective.
- [ ] Hoan thien mapping bien, mien gia tri, constraints, objective function
  - Dung de mo ta formal cho solver/AI.
  - Can cho phan thuyet minh ky thuat.
- [ ] Tong kiem tra truoc nop
  - Dung de chay lai tests, Docker, UI flow, docs.
  - Dam bao khong con loi nghiem trong truoc deadline.

## Priority Goi Y

- P0: Setup, Entity, Auth, Admin employee/account, Shift type, Scheduling period, AI scheduling core.
  - Day la nhom bat buoc de co he thong lap lich chay duoc.
- P1: Leave request, Preference, Manual adjustment, Publish schedule, Export.
  - Day la nhom lam he thong dung duoc trong quy trinh van hanh that.
- P2: Notification, Activity log, PDF, UI polish.
  - Day la nhom tang tinh hoan thien, minh bach va trai nghiem.
- P3: Extra reports, advanced filters, cleanup.
  - Day la nhom cai tien sau khi core da on dinh.

## Milestone Goi Y

- Milestone 1: Chay duoc he thong va auth.
  - Chung minh user co the dang nhap, kich hoat, doi password va goi API protected.
- Milestone 2: Quan ly duoc nhan vien, tai khoan, loai ca, ngay nghi, nguyen vong.
  - Chung minh manager co du input de lap lich.
- Milestone 3: Tao duoc lich thu cong va cong bo lich.
  - Chung minh he thong van dung duoc ngay ca khi AI chua hoan thien.
- Milestone 4: Tao lich tu dong bang AI.
  - Chung minh gia tri toi uu hoa cua de tai.
- Milestone 5: Hoan thien UI, export, bao cao, demo.
  - Chung minh san pham co the trinh bay va ban giao.

## Trang Thai Hien Tai

- Branch dang lam: `phase5/shift-assignment-apis`.
- Phase 0: gan xong, da co monorepo, Docker setup, backend/frontend/AI health scaffold.
- Phase 1: entity chinh da co va da fix mapping theo database design v2.
- Phase 2: auth/security da hoan tat cac flow chinh tren `main` va da bo sung hardening lifecycle.
- Phase 3: da implement employee admin APIs, manager account APIs, admin account APIs cho list/lock/unlock tai khoan nhan vien, shift type APIs, va scheduling rule APIs.
- Phase 4: da co employee leave/preference APIs va manager review leave requests.
- Phase 5: WorkShift + ShiftAssignment slices da xong; SchedulePeriod APIs cho manager da hoan tat o pham vi `DRAFT`; WorkShift APIs da co manual create/list/detail; ShiftAssignment da co manual assign, period schedule view kem assignments, delete assignment, lock/unlock (mutations chi cho phep khi period o DRAFT; schedule view ho tro moi status). Con lai: update-in-place, view-by-employee, approval/publish transitions, AI orchestration.

## Verification Gan Nhat

- Backend command: `./mvnw.cmd clean test`
- Ket qua gan nhat tren branch `phase5/schedule-period-apis`: `249` tests, `0` failures, `0` errors.
