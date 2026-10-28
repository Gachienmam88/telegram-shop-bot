package com.shopbot.telegram;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.UserAccount;
import com.shopbot.service.UserService;
import com.shopbot.util.Formats;

/**
 * Xóa toàn bộ tin nhắn (cả bot lẫn khách) trong chat riêng với mọi khách, bỏ qua admin.
 * Bot API không có lệnh "lấy tin mới nhất" → gửi 1 tin im lặng để biết message_id mới nhất,
 * rồi xóa ngược về trước theo lô 100 tin (gồm cả tin vừa gửi). Telegram chỉ cho bot xóa tin dưới 48h.
 * Chạy song song vài khách cùng lúc nhưng giới hạn tổng ~25 request/giây (dưới ngưỡng ~30/giây của Telegram).
 */
@Component
public class ChatWiper {

    private static final Logger log = LoggerFactory.getLogger(ChatWiper.class);
    private static final int BATCH = 100;
    private static final int WORKERS = 5;
    private static final long MIN_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(40);
    private static final long PROGRESS_EVERY_MS = 5000;
    private static final String PROBE_TEXT = "🧹";

    /**
     * Tiến độ / kết quả: done = số khách đã xử lý (xóa được + không truy cập được).
     * deleteErrors / lastError: số lô Telegram từ chối xóa và câu lỗi gần nhất (để biết vì sao không xóa được).
     */
    public record Result(int total, int done, int wiped, int unreachable, int admins, boolean cancelled,
                         int deleteErrors, String lastError) {
    }

    private final TelegramSender sender;
    private final UserService users;
    private final ShopProperties props;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicInteger deleteErrors = new AtomicInteger();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private long nextSlot;

    public ChatWiper(TelegramSender sender, UserService users, ShopProperties props) {
        this.sender = sender;
        this.users = users;
        this.props = props;
    }

    /** false = đang có lượt xóa khác chạy. */
    public boolean tryStart() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        cancelled.set(false);
        return true;
    }

    /** Yêu cầu dừng lượt đang chạy. false = không có lượt nào đang chạy. */
    public boolean cancel() {
        if (!running.get()) {
            return false;
        }
        cancelled.set(true);
        return true;
    }

    /** Chạy nền; cứ 5 giây sửa tin trạng thái (statusMessageId) để admin thấy tiến độ. */
    @Async
    public void wipeAllAsync(long adminChatId, Integer statusMessageId, int depth) {
        long started = System.nanoTime();
        try {
            Result r = wipeAll(depth, p -> sender.show(adminChatId, statusMessageId, progressText(p, started), null));
            sender.show(adminChatId, statusMessageId, progressText(r, started), null);
        } catch (Exception e) {
            log.error("Wipe chats failed", e);
            sender.send(adminChatId, "❌ Xóa chat bị lỗi giữa chừng: " + e.getMessage(), null);
        } finally {
            running.set(false);
        }
    }

    /** depth = số tin gần nhất (tính cả 2 phía) sẽ quét trong mỗi chat. */
    public Result wipeAll(int depth, Consumer<Result> onProgress) throws InterruptedException {
        List<UserAccount> all = users.all();
        List<Long> customers = all.stream().map(UserAccount::getUserId).filter(id -> !props.isAdmin(id)).toList();
        int admins = all.size() - customers.size();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger wiped = new AtomicInteger();
        AtomicInteger unreachable = new AtomicInteger();
        deleteErrors.set(0);
        lastError.set(null);
        Runnable report = () -> onProgress.accept(new Result(customers.size(), done.get(), wiped.get(),
                unreachable.get(), admins, cancelled.get(), deleteErrors.get(), lastError.get()));

        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        try {
            for (Long chatId : customers) {
                pool.execute(() -> {
                    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    try {
                        (wipeChat(chatId, depth) ? wiped : unreachable).incrementAndGet();
                        done.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            pool.shutdown();
            while (!pool.awaitTermination(PROGRESS_EVERY_MS, TimeUnit.MILLISECONDS)) {
                report.run();
            }
        } finally {
            pool.shutdownNow();
        }
        Result r = new Result(customers.size(), done.get(), wiped.get(), unreachable.get(), admins, cancelled.get(),
                deleteErrors.get(), lastError.get());
        log.info("Wipe chats finished: {}", r);
        return r;
    }

    /** true = đã xóa; false = không gửi được tin dò (khách chặn bot / xóa tài khoản). */
    private boolean wipeChat(long chatId, int depth) throws InterruptedException {
        Integer top;
        try {
            top = call(() -> sender.sendSilentOrThrow(chatId, PROBE_TEXT));
        } catch (TelegramApiException e) {
            log.debug("wipe: cannot reach {}: {}", chatId, e.getMessage());
            return false;
        }
        if (top == null) {
            return false;
        }
        int bottom = Math.max(1, top - depth + 1);
        for (int hi = top; hi >= bottom && !cancelled.get(); hi -= BATCH) {
            List<Integer> ids = IntStream.rangeClosed(Math.max(bottom, hi - BATCH + 1), hi).boxed().toList();
            try {
                call(() -> {
                    sender.deleteManyOrThrow(chatId, ids);
                    return null;
                });
            } catch (TelegramApiException e) {
                // Lô toàn tin quá 48h / không tồn tại → Telegram báo lỗi, bỏ qua và quét tiếp.
                deleteErrors.incrementAndGet();
                if (lastError.getAndSet(e.getMessage()) == null) {
                    log.warn("wipe {} ids {}..{} failed: {}", chatId, ids.get(0), hi, e.getMessage());
                }
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface TelegramCall<T> {
        T run() throws TelegramApiException;
    }

    /** Chờ tới lượt (giới hạn tốc độ chung), gọi Telegram; bị 429 → chờ đúng retry_after rồi thử lại 1 lần. */
    private <T> T call(TelegramCall<T> call) throws TelegramApiException, InterruptedException {
        awaitSlot();
        try {
            return call.run();
        } catch (TelegramApiRequestException e) {
            Integer retryAfter = e.getParameters() == null ? null : e.getParameters().getRetryAfter();
            if (e.getErrorCode() == null || e.getErrorCode() != 429 || retryAfter == null) {
                throw e;
            }
            Thread.sleep(retryAfter * 1000L + 200);
            awaitSlot();
            return call.run();
        }
    }

    /** Mỗi request cách nhau tối thiểu 40ms trên tổng mọi luồng. */
    private void awaitSlot() throws InterruptedException {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlot);
            nextSlot = slot + MIN_INTERVAL_NANOS;
            wait = slot - now;
        }
        if (wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
        }
    }

    static String progressText(Result r, long startedNanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedNanos);
        boolean finished = r.cancelled() || r.done() >= r.total();
        String head = r.cancelled() ? "⛔ <b>Đã dừng xóa chat</b>"
                : finished ? "✅ <b>Đã xóa xong chat</b>" : "🧹 <b>Đang xóa chat...</b>";
        int percent = r.total() == 0 ? 100 : r.done() * 100 / r.total();
        return head
                + "\n📊 Tiến độ: " + r.done() + "/" + r.total() + " khách (" + percent + "%)"
                + "\n✅ Đã xóa: " + r.wiped()
                + "\n🚫 Không truy cập được (chặn bot / xóa tài khoản): " + r.unreachable()
                + "\n👤 Bỏ qua admin: " + r.admins()
                + "\n⏱ Thời gian: " + seconds / 60 + " phút " + seconds % 60 + " giây"
                + (r.deleteErrors() == 0 ? "" : "\n⚠️ Lô Telegram từ chối xóa: " + r.deleteErrors()
                        + "\nLỗi gần nhất: <code>" + Formats.html(String.valueOf(r.lastError())) + "</code>")
                + (finished ? "" : "\n\nDừng: <code>/wipechats STOP</code>");
    }
}
