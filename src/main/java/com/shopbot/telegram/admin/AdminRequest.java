package com.shopbot.telegram.admin;

import org.telegram.telegrambots.meta.api.objects.message.Message;

import com.shopbot.telegram.BotContext;

/**
 * Lệnh admin đã tách: "/addstock 5\nacc1\nacc2" → args = "5", body = "acc1\nacc2".
 */
public record AdminRequest(BotContext ctx, String args, String body, Message message) {

    /** Tách args theo "|" và trim từng phần, vd "🎬 | CapCut" → ["🎬", "CapCut"]. */
    public String[] pipeArgs() {
        return args.isBlank() ? new String[0] : java.util.Arrays.stream(args.split("\\|")).map(String::trim)
                .toArray(String[]::new);
    }

    /** Tách args theo khoảng trắng. */
    public String[] words() {
        return args.isBlank() ? new String[0] : args.trim().split("\\s+");
    }

    public static long parseLong(String s, String fieldName) {
        try {
            return Long.parseLong(s.trim().replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phải là số: " + s);
        }
    }
}
