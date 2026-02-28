package com.stylering.chat;

public enum ChatSessionStatus {
    INTERVIEWING,
    READY_TO_RECOMMEND,
    STOPPED,
    RECOMMENDED;

    public static ChatSessionStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return INTERVIEWING;
        }

        try {
            return ChatSessionStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return INTERVIEWING;
        }
    }
}
