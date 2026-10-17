# Đối chiếu demo với JD Android POS

Nguồn đầu vào: JD Android POS do ứng viên cung cấp (không kèm dữ liệu tuyển dụng riêng trong repo). JD không yêu cầu kinh nghiệm, nhưng yêu cầu Java/Kotlin, Material Design, Android Studio, Retrofit, Dagger, RxJava, Git, kiểm thử và tài liệu.

| Yêu cầu | Minh chứng trong demo | Điểm nên giải thích |
| --- | --- | --- |
| Android trên máy POS | Bán hàng → giỏ → thanh toán → biên lai → lịch sử | App native; terminal và tiền đều giả lập |
| Java/Kotlin | Toàn bộ app Kotlin, Gradle Kotlin DSL | Null safety, data class, enum, Long cho tiền |
| Material Design | XML Material Components, dark theme, nút 48dp, bố cục rộng | Trạng thái loading/error/empty, chữ lớn, insets |
| Android Studio | Gradle Wrapper, SDK 36, minSdk 26, ViewBinding | Cách build APK, run emulator, đọc Logcat |
| Retrofit | `data/CatalogApi.kt`, `di/AppComponent.kt` | GET → JSON → Single; interceptor thay máy chủ demo |
| Dagger | `di/AppComponent.kt` và `PosApplication.kt` | Component, Module, Provides, Binds, Singleton, mã sinh KSP |
| RxJava | `ui/PosViewModel.kt`, `domain/PosRepository.kt` | Single/Flowable/Completable, scheduler, concatMap, Disposable |
| Hiệu suất và ổn định | RecyclerView/ListAdapter, I/O ngoài main, transaction Room | Tránh main-thread DB, không mất tăng số lượng, chống bấm lặp |
| Kiểm thử và sửa lỗi | `app/src/test`, `app/src/androidTest`, báo cáo QA | Tiền, từ chối, lỗi kết nối, replay, rollback, xoay máy |
| Tài liệu kỹ thuật | README, ARCHITECTURE, DEMO_SCRIPT, QA | Giới hạn và hợp đồng rõ, người khác chạy lại được |
| Git | `.gitignore`, source/Wrapper/schema tách build artifact | Repo Git độc lập trên GitHub, tác giả commit `nghiahnc`; không đưa local.properties hoặc build cache vào Git |
| Phối hợp và vòng đời phát triển | Đọc JD → thiết kế → triển khai → test → bàn giao | Đây là dự án cá nhân mô phỏng, không thay thế kinh nghiệm làm nhóm thực tế |

## Câu hỏi kỹ thuật nên chuẩn bị

**Vì sao chọn Kotlin native?** Vì JD tập trung Android và chấp nhận Kotlin. Native giúp trình bày vòng đời Android và cách tích hợp SDK thiết bị; không dùng Flutter thay cho yêu cầu này.

**Retrofit có thực sự được dùng không?** Có. Interface Retrofit khai báo GET, converter Gson giải mã JSON và RxJava3 call adapter trả Single. Interceptor trả response cục bộ trước tầng socket. Không có backend thật trong demo.

**Dagger giúp gì?** Tạo graph dependency tại compile time, cấp cùng database/repository/checkout use case theo phạm vi ứng dụng; dễ thay terminal/repository trong test. KSP sinh mã, không dùng reflection để tự tìm dependency lúc chạy.

**Single khác Flowable thế nào trong app?** Catalog và một kết quả thanh toán chỉ cần Single. Giỏ và lịch sử thay đổi theo DB nên dùng Flowable. Lệnh thêm/xóa không cần trả giá trị nên dùng Completable.

**Tại sao vừa khóa nút vừa dùng mã lần thử?** Khóa nút giúp trải nghiệm và ngăn thao tác lặp trên màn hình. Gate use case ngăn hai subscriber cùng thanh toán. Khóa chính UUID ở DB bảo vệ replay ngay cả khi UI không còn là nơi duy nhất gọi repository.

**Nếu app bị đóng lúc thanh toán?** Terminal hiện là giả lập cục bộ: trước commit thì giỏ còn; sau commit thì biên lai còn và giỏ đã xóa. Với ngân hàng thật, cần thêm lưu attempt trước request và truy vấn/đối soát kết quả chưa rõ; demo chưa triển khai điều đó.

**Vì sao dùng XML thay vì Compose?** Đây là lựa chọn giới hạn phạm vi cho demo này, để tập trung Material Components, ViewBinding, vòng đời và stack nêu trong JD. Không suy diễn rằng công ty đang dùng XML vì JD không ghi rõ.

**Cần làm gì để tích hợp máy POS thật?** Xác nhận vendor/model, SDK và Android API; thêm adapter triển khai `PaymentTerminal`, phân tách I/O khỏi transaction cục bộ, bổ sung trạng thái chờ/đối soát, callback và kiểm thử trên thiết bị thật. Không khẳng định demo đã hỗ trợ đọc chip, NFC hoặc in nhiệt.
