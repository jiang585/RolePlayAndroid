package com.example.roleplaychat.ui.chat;

/**
 * 聊天页用户动作（单向数据流，架构文档 §10.2）。
 */
public enum ChatAction {
    SEND_MESSAGE,
    SEND_NARRATION,
    ADVANCE_AI,
    STOP_GENERATION,
    LOAD_EARLIER,
    CLEAR_CHAT
}
