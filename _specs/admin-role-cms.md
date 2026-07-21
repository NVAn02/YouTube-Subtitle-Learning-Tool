# Spec for Admin Role & CMS

branch: claude/feature/admin-role-cms
figma_component (if used): N/A

## Summary
- Hiện tại hệ thống đã có enum `Role { USER, ADMIN }` trên entity `User`, nhưng chưa có bất kỳ endpoint, luồng nghiệp vụ, hay giao diện nào phân biệt hoặc khai thác quyền `ADMIN` — mọi tài khoản đăng ký qua `/api/auth/register` đều là `USER`, và `SecurityConfig` không có rule riêng cho admin.
- Feature này bổ sung một luồng hoàn chỉnh cho vai trò `ADMIN`: cách một tài khoản trở thành admin, các API chỉ admin mới truy cập được, và một màn hình CMS (Content Management System) trên frontend để admin quản lý dữ liệu của hệ thống (người dùng, video/subtitle đã fetch, từ đã lưu, cache dịch...).
- Mục tiêu là cho phép admin giám sát và quản trị dữ liệu (ví dụ: xem danh sách người dùng, khóa/mở tài khoản, xem/xóa video hoặc bản dịch đã cache, xem thống kê sử dụng) mà không làm thay đổi trải nghiệm của người dùng thường.
- Ngoài ra, CMS cần có một khu vực quản lý log lỗi backend: hệ thống ghi lại các lỗi phát sinh khi vận hành (ví dụ lỗi gọi yt-dlp, lỗi gọi Gemini, exception không mong muốn ở tầng controller/service) vào một nơi lưu trữ bền vững, và admin có thể xem/lọc/xóa các log này qua CMS thay vì phải SSH vào server đọc file log.

## Functional Requirements
- Phân quyền admin:
  - Xác định cách gán vai trò `ADMIN` cho một tài khoản (ví dụ: seed admin đầu tiên qua migration/config, hoặc admin hiện tại có thể thăng cấp user khác).
  - Các endpoint quản trị phải được bảo vệ ở tầng `SecurityConfig`/`@PreAuthorize`, chỉ cho phép `ROLE_ADMIN` truy cập; user thường gọi vào phải nhận lỗi 403.
- API quản trị (backend) cần hỗ trợ tối thiểu:
  - Xem danh sách người dùng (username, role, ngày tạo nếu có), tìm kiếm/phân trang.
  - Thay đổi vai trò của một người dùng (ví dụ nâng lên `ADMIN` hoặc hạ về `USER`).
  - Khóa/mở khóa (vô hiệu hóa) một tài khoản người dùng, ngăn không cho đăng nhập.
  - Xem danh sách video/subtitle đã được fetch và cache trong hệ thống, có thể xóa một video khỏi cache (buộc fetch lại lần sau).
  - Xem/xóa các bản dịch (translation cache) theo từ hoặc theo video.
  - Xem danh sách log lỗi backend đã ghi nhận: thời điểm xảy ra, mức độ nghiêm trọng (ví dụ WARN/ERROR), nguồn gốc lỗi (subtitle fetching, translation, auth...), thông điệp lỗi và stack trace/chi tiết liên quan.
  - Lọc log lỗi theo khoảng thời gian, mức độ nghiêm trọng, hoặc nguồn gốc (module/service).
  - Xóa log lỗi (từng bản ghi hoặc xóa hàng loạt theo bộ lọc) để dọn dẹp dữ liệu cũ.
- Ghi nhận log lỗi:
  - Khi có lỗi phát sinh ở các luồng nghiệp vụ quan trọng (fetch subtitle qua yt-dlp thất bại, gọi Gemini lỗi/timeout, exception không xử lý được ở tầng controller), hệ thống cần ghi lại thông tin lỗi vào nơi lưu trữ mà CMS có thể đọc được — không chỉ ghi ra console/file log của container mà CMS không truy cập tới.
  - Thông tin ghi lại tối thiểu gồm: thời điểm, loại lỗi/exception, thông điệp, ngữ cảnh liên quan (ví dụ videoId đang fetch, từ đang dịch) nếu có, mà không rò rỉ thông tin nhạy cảm (mật khẩu, token, cookie, proxy credentials — nhất quán với cách `SubtitleFetcher.redactCredentials()` đã che thông tin nhạy cảm hiện tại).
- Giao diện CMS (frontend):
  - Một màn hình/khu vực riêng chỉ hiển thị và truy cập được khi tài khoản đăng nhập có vai trò `ADMIN` (ẩn hoàn toàn với user thường, không chỉ ẩn UI mà còn chặn ở route/API).
  - Có các tab/section tương ứng với các API quản trị ở trên: Quản lý người dùng, Quản lý video/subtitle, Quản lý bản dịch, Quản lý log lỗi.
  - Tab log lỗi hiển thị danh sách log (mới nhất trước), cho phép lọc theo thời gian/mức độ/nguồn gốc, và xem chi tiết từng log (thông điệp đầy đủ, stack trace).
  - Các thao tác thay đổi trạng thái (đổi role, khóa tài khoản, xóa cache) cần có xác nhận trước khi thực hiện.
- Đăng nhập/điều hướng:
  - Sau khi đăng nhập, nếu tài khoản là `ADMIN`, người dùng cần có cách vào được CMS (ví dụ: nút/link "Quản trị" chỉ hiện với admin).

## Figma Design Reference (only if referenced)
- Không áp dụng — không có figma hint được cung cấp cho feature này.

## Possible Edge Cases
- Admin tự hạ quyền chính mình xuống `USER` hoặc tự khóa tài khoản của chính mình — cần quyết định có cho phép hay chặn.
- Hệ thống không có admin nào (toàn bộ bị hạ quyền hoặc bị khóa) — cần có cơ chế đảm bảo luôn tồn tại ít nhất một admin hợp lệ.
- Token JWT đã phát hành cho một user trước khi họ được nâng/hạ quyền — quyền trong token cũ có thể không khớp với quyền hiện tại trong DB cho tới khi đăng nhập lại.
- Xóa cache video/translation trong khi có người dùng khác đang xem đúng video đó.
- Phân trang danh sách người dùng/video khi dữ liệu lớn — tránh load toàn bộ bảng cùng lúc.
- User thường cố truy cập trực tiếp URL/route CMS bằng cách gõ tay hoặc gọi thẳng API admin.
- Số lượng log lỗi tăng nhanh (ví dụ yt-dlp lỗi liên tục do YouTube chặn IP) làm bảng log phình to — cần phân trang và/hoặc chính sách giữ log trong một khoảng thời gian nhất định thay vì lưu vô thời hạn.
- Bản thân việc ghi log lỗi bị lỗi (ví dụ mất kết nối DB) không được làm crash luồng nghiệp vụ chính đang xử lý request của người dùng.
- Log lỗi có thể vô tình chứa dữ liệu nhạy cảm nếu exception message lộ ra cookie/token/proxy URL chưa được redact.

## Acceptance Criteria
- Tài khoản không có vai trò `ADMIN` không thể truy cập bất kỳ endpoint quản trị nào (nhận HTTP 403) và không thấy màn CMS trên giao diện.
- Tài khoản có vai trò `ADMIN` đăng nhập được, thấy lối vào CMS, và có thể xem/thao tác trên danh sách người dùng, video/subtitle đã cache, và bản dịch đã cache.
- Thao tác đổi vai trò và khóa/mở tài khoản có xác nhận trước khi thực hiện, và phản ánh đúng trạng thái mới ngay sau khi thực hiện (cả trong DB lẫn trên UI).
- Luôn tồn tại tối thiểu một tài khoản `ADMIN` hợp lệ trong hệ thống tại mọi thời điểm.
- Toàn bộ endpoint quản trị mới đều yêu cầu JWT hợp lệ kèm vai trò `ADMIN`.
- Khi một lỗi phát sinh ở các luồng nghiệp vụ quan trọng (fetch subtitle, gọi Gemini, exception controller), một bản ghi log lỗi tương ứng xuất hiện trong CMS mà không cần SSH vào server.
- Admin lọc được log lỗi theo thời gian/mức độ/nguồn gốc, và có thể xóa log (từng bản ghi hoặc theo bộ lọc).
- Log lỗi hiển thị trên CMS không chứa mật khẩu, token, cookie hay proxy credentials dạng plaintext.

## Open Questions
- Admin đầu tiên được tạo bằng cách nào: seed cứng trong migration/`data.sql`, biến môi trường, hay một lệnh CLI/script riêng?
- Có cần nhiều cấp quyền hơn `USER`/`ADMIN` (ví dụ `MODERATOR`) trong tương lai gần, hay hai cấp là đủ cho phạm vi hiện tại?
- CMS có cần thêm thống kê/dashboard (số lượt dịch, số video được fetch theo ngày...) hay chỉ cần các thao tác quản lý dữ liệu cơ bản?
- Khóa tài khoản (disable) có cần thông báo gì cho người dùng bị khóa khi họ cố đăng nhập không, hay chỉ cần từ chối đăng nhập?
- Xóa "cache" video/subtitle có nghĩa là xóa hẳn record trong DB (buộc fetch + tokenize lại từ đầu), hay chỉ đánh dấu để invalidate?
- Log lỗi nên lưu ở bảng riêng trong MySQL (ví dụ `error_logs`), hay tận dụng/đọc từ cơ chế logging hiện có (Logback/file log) và chỉ hiển thị qua CMS?
- Cần ghi log lỗi cho mọi exception không bắt được (global exception handler) hay chỉ giới hạn ở các luồng nghiệp vụ đã liệt kê (yt-dlp, Gemini)?
- Có cần chính sách tự động xóa log cũ (retention, ví dụ giữ 30 ngày) hay admin tự xóa thủ công là đủ?

## Testing Guidelines
Create a test file(s) in the ./tests folder for the new feature, and create meaningful tests for the following cases, without going too heavy:
- User thường gọi API quản trị (ví dụ danh sách người dùng) phải nhận HTTP 403.
- Admin gọi API quản trị hợp lệ nhận được dữ liệu đúng định dạng mong đợi (danh sách người dùng, video, bản dịch).
- Đổi vai trò một user từ `USER` sang `ADMIN` (và ngược lại) qua API cập nhật đúng trong DB.
- Khóa tài khoản khiến người dùng đó không thể đăng nhập được nữa (login trả về lỗi phù hợp).
- Xóa một video/bản dịch khỏi cache qua CMS thực sự xóa khỏi DB và một lần fetch/dịch sau đó tạo lại record mới.
- Một lỗi giả lập (ví dụ fetch subtitle với video ID không tồn tại) tạo ra đúng một bản ghi log lỗi có thể truy vấn được qua API/CMS.
- Lọc log lỗi theo mức độ nghiêm trọng hoặc khoảng thời gian trả về đúng tập kết quả mong đợi.
- Xóa log lỗi qua CMS thực sự xóa bản ghi khỏi nơi lưu trữ.
