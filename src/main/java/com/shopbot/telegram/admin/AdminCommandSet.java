package com.shopbot.telegram.admin;

import java.util.Map;

/** 1 nhóm lệnh admin: map "/lenh" → hàm xử lý. */
public interface AdminCommandSet {

    @FunctionalInterface
    interface AdminCommand {
        void run(AdminRequest request) throws Exception;
    }

    Map<String, AdminCommand> commands();
}
