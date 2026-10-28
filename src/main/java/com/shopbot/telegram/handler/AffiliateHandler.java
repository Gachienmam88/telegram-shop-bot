package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;
import com.shopbot.service.AffiliateService;
import com.shopbot.service.MessageService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;

/** Màn Affiliate: link giới thiệu, số người đã giới thiệu, hoa hồng. */
@Component
public class AffiliateHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final ShopKeyboards keyboards;
    private final AffiliateService affiliate;
    private final ShopProperties props;

    public AffiliateHandler(TelegramSender sender, MessageService msg, ShopKeyboards keyboards,
                            AffiliateService affiliate, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.affiliate = affiliate;
        this.props = props;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.AFFILIATE);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        AffiliateService.Stats s = affiliate.stats(ctx.userId());
        String rate = s.percent() > 0
                ? msg.t(ctx.lang(), "affiliate.rate", s.percent())
                : msg.t(ctx.lang(), "affiliate.disabled");
        String text = msg.t(ctx.lang(), "affiliate.text", props.deepLink("ref_" + ctx.userId()), s.referredCount(),
                Formats.money(s.totalCommission()), rate);
        sender.show(ctx.chatId(), ctx.messageId(), text, keyboards.backHome(ctx.lang()));
    }
}
