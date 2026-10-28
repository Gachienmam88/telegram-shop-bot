package com.shopbot.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * Lấy câu chữ theo ngôn ngữ user từ i18n/messages_*.properties.
 * File gốc messages.properties là tiếng Việt → key thiếu ở ngôn ngữ khác tự fallback về tiếng Việt.
 */
@Service
public class MessageService {

    public static final List<String> SUPPORTED_LANGS = List.of("vi", "en", "th", "zh", "ko");
    public static final String DEFAULT_LANG = "vi";

    private final MessageSource messageSource;

    public MessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String t(String lang, String key, Object... args) {
        // Truyền String để MessageFormat không tự format số kiểu "1,000".
        Object[] stringArgs = Arrays.stream(args).map(String::valueOf).toArray();
        return messageSource.getMessage(key, stringArgs, Locale.forLanguageTag(normalize(lang)));
    }

    public static String normalize(String lang) {
        return lang != null && SUPPORTED_LANGS.contains(lang) ? lang : DEFAULT_LANG;
    }
}
