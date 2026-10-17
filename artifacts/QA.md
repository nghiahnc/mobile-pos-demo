# Nghĩa POS — kết quả kiểm chứng

Ngày kiểm chứng: **16/10/2026**, múi giờ Asia/Bangkok. Bản **1.0.0-demo**, package `vn.nghia.pos`.

![Tổng quan giao diện](preview.png)

## Kết quả cuối

| Kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- |
| Build APK debug | Đạt | final-build.log (`final-build.log`, lưu cục bộ), APK (`NghiaPOS-debug.apk`, lưu cục bộ) |
| Unit test | **15/15 đạt** | Báo cáo HTML (`index.html`, lưu cục bộ) |
| Integration test trên Android | **10/10 đạt** | functional-device-tests.log (`functional-device-tests.log`, lưu cục bộ) |
| Luồng UI và recreation/rotation | **2/2 đạt** | functional-device-tests.log (`functional-device-tests.log`, lưu cục bộ) |
| Kiểm tra bố cục/thao tác | **7/7 cấu hình đạt** | Log từng cấu hình (`phone.log`, lưu cục bộ), bảng dưới |
| Android lint | **0 lỗi, 30 cảnh báo** | Báo cáo HTML đầy đủ (`lint-results-debug.html`, lưu cục bộ), bản text (`lint-results-debug.txt`, lưu cục bộ) |
| Dữ liệu sau đóng process/mở lại | Đạt: giỏ còn 3 món, tổng 95.000 ₫ từ dữ liệu QA | Kiểm tra UI hierarchy sau `am force-stop` rồi `am start` |
| Quyền truy cập mạng | APK không yêu cầu `android.permission.INTERNET` | Kiểm tra manifest APK bằng `aapt dump permissions` |

Tổng hợp máy đọc được: verification-summary.json (`verification-summary.json`, lưu cục bộ). Mã kiểm tra APK/source ZIP: SHA256SUMS.txt (`SHA256SUMS.txt`, lưu cục bộ).

## Các trường hợp nghiệp vụ đã chạy

- Tổng đơn bằng số nguyên VND, số lượng 1–99, giỏ rỗng, tiền thiếu/âm/không nhập, giá sai và tràn số.
- Tiền mặt 50.000 ₫, khách đưa 100.000 ₫ → tiền thừa 50.000 ₫; đơn 25.000 ₫ → tiền thừa 75.000 ₫.
- Thẻ bị từ chối hoặc lỗi kết nối giữ nguyên giỏ, ghi kết quả thất bại; thử lại thành công mới xóa giỏ.
- Hai lần bấm thanh toán liên tiếp chỉ thêm một giao dịch thành công.
- Hai request cùng attempt ID chạy đồng thời chỉ ghi một biên lai; replay ID cũ không xóa giỏ mới.
- Terminal ném exception: transaction rollback, không có biên lai và không mất giỏ.
- Database đóng rồi mở lại vẫn giữ giỏ/biên lai. Xoay màn hình và Activity recreation giữ từ khóa, phương thức/kịch bản thẻ.
- Doanh thu chỉ cộng thành công: sau luồng mẫu là 75.000 ₫, gồm 2 thành công trong 3 lần thử.

## Bố cục và thao tác

Thực thi trên **Medium_Phone, Android 37.1 (API 37)**, x86_64, system image 16KB. Các kích thước tablet/điện thoại nhỏ bên dưới được mô phỏng bằng `adb wm size/density` trên cùng emulator, không phải thiết bị vật lý khác nhau. Mỗi cấu hình kiểm tra thêm món, mở giỏ, mở checkout, lịch sử và vùng chạm CTA tối thiểu 48dp.

| Cấu hình | Kích thước / cỡ chữ | Kết quả và ảnh |
| --- | --- | --- |
| Điện thoại sáng | 1080×2400 px, density 420, font 100% | Đạt (`phone.log`, lưu cục bộ) · [Thực đơn](screenshots/phone-catalog.png) |
| Điện thoại tối | Như trên, night mode | Đạt (`dark.log`, lưu cục bộ) · [Ảnh tối](screenshots/dark-catalog.png) |
| Chữ lớn | Font scale **200%** | Đạt (`large-text.log`, lưu cục bộ) · [Giỏ](screenshots/large-text-cart.png) · [Thanh toán](screenshots/large-text-checkout.png) |
| Điện thoại nhỏ | 720×1280 px, density 320 → 360×640dp | Đạt (`small-phone.log`, lưu cục bộ) · [Ảnh](screenshots/small-phone-catalog.png) |
| Tablet dọc | 1000×1400 px, density 160 | Đạt (`tablet-portrait.log`, lưu cục bộ) · [Hai cột nghiệp vụ](screenshots/tablet-portrait-catalog.png) |
| Tablet ngang | 1400×900 px, density 160 | Đạt (`tablet-landscape.log`, lưu cục bộ) · [Hai cột nghiệp vụ](screenshots/tablet-landscape-catalog.png) |
| Điện thoại ngang | 1280×720 px, density 320 → 640×360dp | Đạt (`phone-landscape.log`, lưu cục bộ) · [Thực đơn](screenshots/phone-landscape-catalog.png) · [Lịch sử](screenshots/phone-landscape-history.png) |

Đã xem ảnh chụp của cả theme sáng/tối, chữ 200%, phone nhỏ, tablet và landscape. Chữ lớn chuyển thực đơn về ít cột, tách thành tiền khỏi hàng nút số lượng và xếp phương thức thanh toán dọc để nhãn không bị chen ép. Chiều rộng 840dp trở lên có giỏ cạnh danh mục; màn hình thấp thu gọn tiêu đề. Màn hình cuộn có thể chỉ hiển thị một phần nội dung tại một thời điểm; các phần còn lại truy cập bằng cuộn.

Các kiểm thử UI chạy với animation hệ thống tắt, tương đương `testOptions.animationsDisabled`, để đồng bộ thao tác qua rotation; app không có animation tùy biến bắt buộc. Script đã khôi phục font 100%, kích thước gốc, theme và animation sau khi kết thúc.

Các cặp chữ/nền chủ đạo ở cả hai theme được tính tương phản từ token màu, ngưỡng tối thiểu 4.5:1; số đo trong `verification-summary.json`. Đây không phải chứng nhận accessibility toàn diện hoặc kiểm thử TalkBack thủ công.

## Ảnh luồng phỏng vấn

[Thực đơn](screenshots/01-catalog.png) → [Giỏ 50.000 ₫](screenshots/02-cart.png) → [Thẻ bị từ chối](screenshots/03-card-declined.png) → [Thử lại](screenshots/04-card-checkout.png) → [Biên lai](screenshots/05-receipt-card.png) → [Tiền mặt](screenshots/06-cash-checkout.png) → [Biên lai tiền mặt](screenshots/07-receipt-cash.png) → [Lịch sử](screenshots/08-history.png).

## Các giới hạn còn lại

- Thanh toán giả lập; chưa kiểm thử SDK đầu đọc thẻ, ngân hàng hoặc máy in POS. Không có giao dịch tiền thật.
- minSdk 26/Android 8 được kiểm tra bằng lint và cấu hình build; chưa chạy trên thiết bị Android 8 thực tế.
- 30 cảnh báo lint còn lại gồm gợi ý nâng phiên bản/target SDK (11), khả năng bản địa hóa chuỗi (10), tối ưu layout/overdraw (4), resource chưa dùng (2), check-result trong test (2) và khai báo backup mới (1). Không vô hiệu hóa lint hoặc tạo baseline để giấu lỗi. targetSdk 36 và dependency khai báo cụ thể theo phạm vi bản demo.
- Dữ liệu QA là giả; APK cài mới bắt đầu với giỏ và lịch sử trống. Script kiểm thử xóa dữ liệu riêng app trước khi tạo mẫu.

> Bản GitHub chỉ giữ ảnh/báo cáo đã rà soát; các log và tệp bằng chứng ghi là ‘lưu cục bộ’ không được công khai.
