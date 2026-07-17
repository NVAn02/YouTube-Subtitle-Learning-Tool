# Plan chi tiết: YouTube Subtitle Learning Tool (bản đơn giản)

## 1. Mục tiêu

Ứng dụng web đơn trang: người dùng dán link YouTube → xem video kèm phụ đề đồng bộ → click vào từng từ (đã được tách bằng NLP) → nhận nghĩa dịch theo ngữ cảnh câu (qua Gemini).

Không có tài khoản, không lưu lịch sử — xử lý on-the-fly.

**Công nghệ sử dụng:**
- **Backend:** Java Spring Boot, Apache OpenNLP (tokenize), LangChain4j + Gemini (dịch)
- **Frontend:** Vue.js
- **Database:** Không dùng ở bản đầu (in-memory cache nếu cần)

## 2. Kiến trúc tổng thể

```
[Vue.js SPA]
   │  REST (Axios)
   ▼
[Spring Boot Backend]
   ├── SubtitleController      → nhận link, trả subtitle đã tokenize
   ├── SubtitleFetcher         → lấy phụ đề thô từ YouTube (timedtext)
   ├── SubtitleParser          → parse XML/VTT → list {start, end, text}
   ├── NlpService (OpenNLP)    → tokenize từng câu phụ đề
   ├── TranslateController     → nhận word + sentence, trả nghĩa
   ├── TranslationService (LangChain4j + Gemini) → dịch theo ngữ cảnh
   └── (optional) In-memory cache (ConcurrentHashMap) cho bản dịch
```

## 3. Luồng xử lý chi tiết

**Bước 1 – Nhập link**
Người dùng dán URL YouTube vào FE → FE gọi `POST /api/subtitles` với body `{ youtubeUrl }`.

**Bước 2 – Backend lấy phụ đề thô**
- Parse `youtube_id` từ URL (regex).
- Gọi endpoint `timedtext` của YouTube (không chính thức) để lấy phụ đề dạng XML/VTT.
- Xử lý trường hợp không có phụ đề (trả lỗi rõ ràng cho FE hiển thị thông báo).

**Bước 3 – Parse phụ đề thô**
- Convert XML/VTT → danh sách object `{ start, end, text }` (thời gian tính bằng ms).
- Chuẩn hóa text (decode HTML entity, bỏ tag thừa nếu có).

**Bước 4 – Tokenize bằng OpenNLP**
- Với mỗi `text` của từng câu phụ đề, dùng `TokenizerME` (model `en-token.bin`) để tách thành mảng token.
- Gắn mảng `tokens` vào từng object subtitle.
- Kết quả trả về FE: `{ start, end, text, tokens: [...] }`.

**Bước 5 – FE render**
- Nhúng video bằng YouTube IFrame Player API.
- Danh sách subtitle hiển thị dạng danh sách câu; câu đang phát (theo `currentTime` của player) được highlight.
- Mỗi token trong câu render thành 1 phần tử riêng, có thể click (bỏ qua click cho token là dấu câu thuần).

**Bước 6 – Click từ để dịch**
- FE gửi `POST /api/translate` với `{ word, sentence, targetLang }`.
- Backend check cache (nếu có) → nếu miss, gọi Gemini qua LangChain4j với prompt chứa từ + câu ngữ cảnh.
- Trả về `{ translation }`, FE hiển thị tooltip cạnh từ vừa click.

## 4. Thiết kế API

### `POST /api/subtitles`

**Request:**
```json
{ "youtubeUrl": "https://youtube.com/watch?v=..." }
```

**Response:**
```json
{
  "videoId": "abc123",
  "subtitles": [
    {
      "start": 1200,
      "end": 3400,
      "text": "I don't like it.",
      "tokens": ["I", "do", "n't", "like", "it", "."]
    }
  ]
}
```

**Error case:** 404 nếu video không có phụ đề

### `POST /api/translate`

**Request:**
```json
{ "word": "like", "sentence": "I don't like it.", "targetLang": "vi" }
```

**Response:**
```json
{ "translation": "thích" }
```

## 5. Thiết kế module Backend

| Module | Trách nhiệm | Ghi chú |
|---|---|---|
| `SubtitleController` | Nhận request, gọi service, trả response | REST layer |
| `SubtitleFetcher` | Gọi YouTube `timedtext`, trả raw XML/VTT | Có thể fail nếu video không có phụ đề → xử lý exception rõ ràng |
| `SubtitleParser` | Parse raw → list `{start, end, text}` | Dùng thư viện XML parser có sẵn hoặc regex đơn giản |
| `NlpService` | Load model OpenNLP 1 lần lúc khởi động (singleton bean), expose method `tokenize(String sentence)` | Model load 1 lần, tránh load lại mỗi request |
| `TranslateController` | Nhận word/sentence, gọi TranslationService | REST layer |
| `TranslationService` | Dùng LangChain4j `AiServices` interface gọi Gemini | Prompt đơn giản: dịch từ theo ngữ cảnh câu, trả text ngắn gọn |
| `TranslationCache` *(optional)* | `ConcurrentHashMap<String, String>` key = hash(word+sentence+targetLang) | Tránh gọi Gemini lặp lại trong cùng phiên chạy app |

## 6. Thiết kế Frontend (Vue.js)

**Component chính:** `App.vue` hoặc `SubtitleLearningPage.vue` — vì chỉ có 1 trang, không cần router phức tạp.

**Các phần con:**
- Input link YouTube + nút submit
- Player component: nhúng YouTube IFrame API, expose `currentTime` ra ngoài (polling ~250-500ms hoặc dùng event của player)
- Subtitle list component: nhận `subtitles` từ API, tự tính câu nào đang active dựa vào `currentTime`, render từng token thành phần tử click được
- Tooltip/popup component: hiển thị nghĩa khi click từ, tự ẩn khi click ra ngoài hoặc click từ khác

**State cần quản lý** (dùng `ref`/`reactive` đơn giản, chưa cần Pinia):
- `subtitles`: danh sách câu + token
- `currentTime`: thời gian hiện tại của video
- `activeSentenceIndex`: câu đang được highlight
- `selectedWord`, `selectedTranslation`, `tooltipPosition`: cho popup dịch

## 7. Chuẩn bị tài nguyên trước khi code

- Tải model OpenNLP tokenizer tiếng Anh (`en-token.bin`) từ kho model chính thức của Apache OpenNLP, đặt vào `src/main/resources/models/`.
- Lấy API key Gemini, cấu hình trong `application.yml`/`application.properties`, không hardcode trong code.
- Cấu hình LangChain4j dependency cho Gemini model trong `pom.xml`.
- Cấu hình CORS ở Spring Boot để FE (Vue dev server) gọi được API.

## 8. Xử lý edge case cần lên kế hoạch trước

| Tình huống | Hướng xử lý |
|---|---|
| Video không có phụ đề | Backend trả lỗi rõ ràng (404 + message), FE hiển thị thông báo "Video này không có phụ đề" |
| Link không hợp lệ / không parse được video ID | Validate ở FE trước khi gọi API, và validate lại ở BE |
| Token là dấu câu (`.`, `,`, `!`) | Không cho click, không gọi API dịch |
| Gemini timeout / lỗi API | Có try-catch, trả message lỗi cho FE hiển thị "Không dịch được, thử lại" |
| Click liên tục nhiều từ nhanh | Cân nhắc debounce hoặc hủy request cũ nếu request mới được gửi |
| Phụ đề không phải tiếng Anh | Ở bản đầu, giới hạn chỉ hỗ trợ tiếng Anh (vì chỉ có model tokenizer tiếng Anh); ghi chú rõ giới hạn này |

## 9. Thứ tự triển khai đề xuất

1. **Backend – lấy phụ đề thô:** viết `SubtitleFetcher` + `SubtitleParser`, test độc lập bằng Postman/console, chưa cần tokenize.
2. **Backend – tokenize:** thêm `NlpService`, tích hợp vào response của `/api/subtitles`.
3. **Backend – dịch từ:** viết `TranslationService` với LangChain4j + Gemini, expose `/api/translate`, test độc lập bằng Postman.
4. **Frontend – khung trang:** input link + nhúng video YouTube, chưa cần phụ đề.
5. **Frontend – hiển thị phụ đề tĩnh:** gọi `/api/subtitles`, render danh sách câu (chưa đồng bộ theo thời gian).
6. **Frontend – đồng bộ theo thời gian:** highlight câu hiện tại dựa vào `currentTime`.
7. **Frontend – click từ dịch:** render token, bắt sự kiện click, gọi `/api/translate`, hiện tooltip.
8. **Kiểm thử toàn luồng end-to-end**, xử lý các edge case ở mục 8.
9. **(Giai đoạn sau, không bắt buộc):** thêm MySQL cache bản dịch, thêm tài khoản người dùng, sổ từ vựng cá nhân.

## 10. Phạm vi KHÔNG làm ở bản này (giữ đơn giản)

- Không có đăng nhập/tài khoản
- Không lưu trữ MySQL (dùng in-memory cache nếu cần)
- Không POS-tagging, không lemmatization (chỉ tokenize)
- Không hỗ trợ đa ngôn ngữ phụ đề (chỉ tiếng Anh ở bản đầu)
- Không có sổ từ vựng cá nhân / flashcard
- Không cần responsive/mobile tối ưu ở bản đầu

## 11. Hướng mở rộng sau này (giai đoạn 2+)

| Tính năng thêm | Khi nào nên làm |
|---|---|
| MySQL cache bản dịch (`translation_cache`) | Khi thấy gọi Gemini lặp lại nhiều, tốn chi phí |
| Lưu video/subtitle vào DB | Khi muốn tránh fetch lại phụ đề cho video đã xem |
| Tài khoản người dùng | Khi muốn cá nhân hóa, lưu lịch sử |
| Sổ từ vựng cá nhân / flashcard | Khi muốn hỗ trợ ôn tập từ đã học |
| POS-tag + Lemmatization | Khi muốn cache dịch theo từ gốc, giảm gọi API trùng lặp |
| Hỗ trợ đa ngôn ngữ phụ đề | Khi cần mở rộng ra người học ngôn ngữ khác ngoài tiếng Anh |
