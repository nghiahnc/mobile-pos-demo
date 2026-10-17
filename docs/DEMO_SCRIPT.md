# Kịch bản phỏng vấn 5 phút

Trước buổi phỏng vấn: cài `artifacts/NghiaPOS-debug.apk`, mở app, kiểm tra theme và pin. Có thể bật chế độ máy bay; app vẫn chạy đầy đủ. Nếu muốn các con số khớp hoàn toàn bên dưới, bắt đầu từ bản cài sạch hoặc xóa dữ liệu **riêng Nghĩa POS** trong Android Settings → Apps → Nghĩa POS → Storage → Clear storage. Thao tác này xóa giỏ/lịch sử demo hiện có.

## 0:00–0:30 · Giới thiệu đúng phạm vi

“Em đọc JD có yêu cầu Android cho máy POS nên chuẩn bị app bán hàng tại quầy bằng Kotlin native. App dùng Material, MVVM, Retrofit, Dagger, RxJava và Room. Đây là môi trường mô phỏng, không có thanh toán thật hoặc SDK ngân hàng.”

Chỉ nhanh danh mục 12 món, tìm `ca phe`, lọc Cà phê; xóa từ khóa để xem lại toàn bộ. Không giới thiệu đây là sản phẩm đã triển khai cho cửa hàng thật.

## 0:30–1:20 · Tạo đơn

Thêm **Cà phê đen × 2**, mở giỏ. Tổng phải là **50.000 ₫**. Tăng lên 3 rồi giảm về 2 để minh họa cập nhật tổng và Room. Xoay màn hình hoặc mở lại app; hai món vẫn còn.

“Tiền lưu bằng số nguyên VND. Giỏ nằm trong database cục bộ; các lần sửa được xử lý tuần tự để không mất số lượng khi bấm nhanh.”

## 1:20–2:20 · Thanh toán lỗi rồi thử lại

Nhấn Thanh toán → **Thẻ mô phỏng** → **Bị từ chối** → Xác nhận. Sau khoảng một giây app báo lý do; giỏ vẫn giữ 2 ly. Chọn **Thành công**, xác nhận lại. Trong khi xử lý, nút bị khóa; sau đó mở biên lai **50.000 ₫**.

“Lần thất bại được lưu nhưng không cộng doanh thu. Mỗi lần thử có mã riêng; cùng mã gửi lại trả kết quả đã lưu. Thành công ghi biên lai và xóa giỏ trong cùng transaction.”

Không dùng nút “Tạo đơn mới” để khẳng định đã xóa dữ liệu; nút này chỉ điều hướng về thực đơn.

## 2:20–3:10 · Tiền mặt và lịch sử

Tạo đơn mới **Cà phê đen × 1**, chọn Tiền mặt, nhập **100000**. Tiền thừa phải là **75.000 ₫**. Xác nhận, mở lịch sử: **75.000 ₫ doanh thu**, **2 giao dịch thành công / 3 lần thử** nếu bắt đầu từ dữ liệu trống. Chạm một giao dịch để mở lại biên lai, có cả lần bị từ chối.

## 3:10–4:20 · Đi qua mã nguồn

Mở sẵn các lớp:

1. `ui/PosViewModel.kt`: đầu vào giao diện, hàng đợi Rx, state và vòng đời.
2. `domain/Models.kt`, `domain/PosRepository.kt`: tiền, trạng thái, hợp đồng repository và chặn đồng thời.
3. `data/LocalPosRepository.kt`: transaction, UUID, snapshot biên lai và giữ giỏ khi lỗi.
4. `data/CatalogApi.kt`, `di/AppComponent.kt`: Retrofit cục bộ và graph Dagger sinh qua KSP.

“Em dùng interface để thay được nguồn dữ liệu hoặc terminal. Demo chạy độc lập nên nhà tuyển dụng cài APK là thử được.”

## 4:20–5:00 · Kiểm thử và hướng mở rộng

Mở `artifacts/QA.md`, unit tests và integration tests. Nêu ba kiểm tra quan trọng: từ chối không mất giỏ; replay UUID không ghi thêm; DB mở lại vẫn có lịch sử.

“Nếu tích hợp thiết bị thật, em sẽ làm theo SDK vendor, lưu trạng thái chờ trước khi gọi thanh toán, xử lý kết quả chưa rõ và đối soát. Phần đó hiện chưa có trong demo.”

Kết thúc bằng việc hỏi nhà tuyển dụng về model POS, phiên bản Android và SDK đang sử dụng để trao đổi hướng tích hợp phù hợp.
