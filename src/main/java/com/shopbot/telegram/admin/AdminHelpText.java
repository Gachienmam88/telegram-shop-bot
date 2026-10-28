package com.shopbot.telegram.admin;

/** Nội dung lệnh /admin: cú pháp + ví dụ (bấm vào ví dụ để copy). */
final class AdminHelpText {

    static final String TEXT = """
            🛠 <b>LỆNH ADMIN</b>
            <i>Bấm vào ví dụ để copy. Giá/số tiền ghi đủ số: 50000 (không ghi 50k).</i>

            <b>Danh mục</b>
            /addcat emoji | Tên [| thứ tự] — thêm danh mục
            VD: <code>/addcat 🎬 | CapCut | 2</code>
            /cats — xem danh mục + id
            VD: <code>/cats</code>

            <b>Sản phẩm</b>
            /addproduct cat_id | emoji | Tên SP | giá [⏎ mô tả nhiều dòng] — thêm SP
            VD: <code>/addproduct 1 | ✂️ | CapCut Pro 1 tháng | 220000
            ✅ Bảo hành 30 ngày
            Định dạng: Tài khoản|Mật khẩu</code>
            /plist [cat_id] — xem SP (id, giá, tồn, đã bán)
            VD: <code>/plist</code> hoặc <code>/plist 1</code>
            /setdesc id ⏎ mô tả nhiều dòng (HTML: &lt;b&gt;, &lt;i&gt;, &lt;a href&gt;...)
            VD: <code>/setdesc 1
            &lt;b&gt;Grok 4 dùng 4 ngày&lt;/b&gt;
            Bảo hành 24h</code>
            /setprice id giá — đổi giá
            VD: <code>/setprice 1 45000</code>
            /setname id tên mới — đổi tên
            VD: <code>/setname 1 Grok 4 (4 ngày)</code>
            /hide id — /show id — ẩn/hiện SP
            VD: <code>/hide 1</code> — <code>/show 1</code>

            <b>Kho</b>
            /addstock id ⏎ mỗi dòng 1 tài khoản
            VD: <code>/addstock 1
            user1@gmail.com|Pass001
            user2@gmail.com|Pass002</code>
            (hoặc gửi file .txt kèm caption <code>/addstock 1</code>)
            → tự thông báo hàng về cho group + khách
            /stock id — xem tồn kho
            VD: <code>/stock 1</code>
            /delstock id — xóa mềm TẤT CẢ hàng còn trống
            VD: <code>/delstock 1</code>
            /delstock id ⏎ các tài khoản — xóa mềm từng tài khoản
            VD: <code>/delstock 1
            user1@gmail.com|Pass001</code>

            <b>Thanh toán</b>
            /confirm mã — xác nhận tay đơn (DH...) hoặc lệnh nạp (NAP...)
            VD: <code>/confirm DHYUC9M9CW</code>
            /addbal user_id số_tiền — cộng (âm = trừ) ví
            VD: <code>/addbal 123456789 50000</code> — <code>/addbal 123456789 -20000</code>
            /setaff % — hoa hồng affiliate (0 = tắt)
            VD: <code>/setaff 5</code> — <code>/setaff 0</code>
            /stats — thống kê
            VD: <code>/stats</code>

            <b>Thu mua acc TikTok & Rút tiền</b>
            Khi khách gửi hồ sơ bán acc hoặc yêu cầu rút tiền, bot gửi kèm nút <b>Duyệt/Từ chối</b> (bán acc) và <b>Đã chuyển/Từ chối</b> (rút tiền) — bấm trực tiếp để xử lý.
            • Duyệt hồ sơ bán acc = cộng đúng giá khách đề nghị vào ví của họ.
            • Đã chuyển (rút tiền) = khách nhận nút xác nhận đã nhận; Từ chối = hoàn tiền về ví.

            <b>Dọn chat</b>
            /wipechats XACNHAN [số tin] — xóa tin nhắn (cả 2 phía) với MỌI khách, không xóa chat admin, chỉ xóa được tin dưới 48h
            VD: <code>/wipechats XACNHAN</code> — <code>/wipechats XACNHAN 3000</code>
            /wipechats STOP — dừng lượt xóa đang chạy
            """;

    private AdminHelpText() {
    }
}
