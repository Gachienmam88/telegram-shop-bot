package com.shopbot.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.AccountSubmission;
import com.shopbot.domain.BalanceReason;
import com.shopbot.domain.SubmissionStatus;
import com.shopbot.repository.AccountSubmissionRepository;
import com.shopbot.util.CodeGenerator;

/**
 * Thu mua tài khoản TikTok: người dùng gửi email/SĐT + username + giá muốn bán,
 * admin duyệt tay. Khi duyệt, giá người bán đưa ra được cộng vào ví để họ tự
 * rút.
 */
@Service
public class AccountSaleService {

    private final AccountSubmissionRepository submissions;
    private final WalletService wallet;
    private final CodeGenerator codes;
    private final Clock clock;

    public AccountSaleService(AccountSubmissionRepository submissions, WalletService wallet, CodeGenerator codes,
            Clock clock) {
        this.submissions = submissions;
        this.wallet = wallet;
        this.codes = codes;
        this.clock = clock;
    }

    @Transactional
    public AccountSubmission create(long userId, long chatId, String contact, String username, long askingPrice) {
        AccountSubmission s = new AccountSubmission();
        s.setCode(codes.payRef(CodeGenerator.SUBMISSION_PREFIX));
        s.setUserId(userId);
        s.setContact(contact);
        s.setAccountPassword(username);
        s.setAskingPrice(askingPrice);
        s.setStatus(SubmissionStatus.PENDING);
        s.setChatId(chatId);
        s.setCreatedAt(clock.instant());
        return submissions.save(s);
    }

    /** Duyệt: cộng giá người bán vào ví. Trả về hồ sơ đã duyệt. */
    @Transactional
    public AccountSubmission accept(String code, long adminId) {
        AccountSubmission s = pending(code);
        s.setStatus(SubmissionStatus.ACCEPTED);
        s.setDecidedAt(clock.instant());
        s.setDecidedBy(adminId);
        wallet.credit(s.getUserId(), s.getAskingPrice(), BalanceReason.ACCOUNT_SALE, s.getCode());
        return s;
    }

    @Transactional
    public AccountSubmission reject(String code, long adminId) {
        AccountSubmission s = pending(code);
        s.setStatus(SubmissionStatus.REJECTED);
        s.setDecidedAt(clock.instant());
        s.setDecidedBy(adminId);
        return s;
    }

    private AccountSubmission pending(String code) {
        AccountSubmission s = submissions.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ " + code));
        if (s.getStatus() != SubmissionStatus.PENDING) {
            throw new IllegalArgumentException("Hồ sơ " + code + " đã được xử lý rồi");
        }
        return s;
    }
}
