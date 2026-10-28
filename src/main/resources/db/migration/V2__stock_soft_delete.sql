-- Soft delete tài khoản trong kho: status = 'DELETED' + thời điểm xóa, dữ liệu vẫn giữ lại để tra cứu.
ALTER TABLE stock_items ADD COLUMN deleted_at TIMESTAMPTZ;
