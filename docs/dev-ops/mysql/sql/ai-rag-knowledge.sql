-- 创建数据库（如果还没有）
CREATE DATABASE xfg_frame_archetype;

-- 会话表（对应左侧聊天窗口列表）
CREATE TABLE chat_conversation (
    id          BIGSERIAL       PRIMARY KEY,
    title       VARCHAR(200)    NOT NULL DEFAULT '新对话',
    user_id     VARCHAR(100)    NOT NULL DEFAULT 'default',
    status      SMALLINT        NOT NULL DEFAULT 0,  -- 0-正常, 1-删除
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  chat_conversation         IS '聊天会话表';
COMMENT ON COLUMN chat_conversation.id      IS '会话ID';
COMMENT ON COLUMN chat_conversation.title   IS '会话标题';
COMMENT ON COLUMN chat_conversation.user_id IS '用户标识';
COMMENT ON COLUMN chat_conversation.status  IS '状态：0-正常，1-删除';
COMMENT ON COLUMN chat_conversation.created_at IS '创建时间';
COMMENT ON COLUMN chat_conversation.updated_at IS '最后活跃时间';

CREATE INDEX idx_conversation_user_id ON chat_conversation (user_id);
CREATE INDEX idx_conversation_updated_at ON chat_conversation (updated_at DESC);

-- 聊天消息表（对应每个会话中的消息）
CREATE TABLE chat_message (
    id              BIGSERIAL       PRIMARY KEY,
    conversation_id BIGINT          NOT NULL REFERENCES chat_conversation(id) ON DELETE CASCADE,
    role            VARCHAR(20)     NOT NULL,  -- user / assistant / system
    content         TEXT            NOT NULL,
    metadata        JSONB,                     -- 额外信息，如 token 用量、引用文档等
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  chat_message                 IS '聊天消息表';
COMMENT ON COLUMN chat_message.id              IS '消息ID';
COMMENT ON COLUMN chat_message.conversation_id IS '所属会话ID';
COMMENT ON COLUMN chat_message.role            IS '角色：user-用户, assistant-AI, system-系统';
COMMENT ON COLUMN chat_message.content         IS '消息内容';
COMMENT ON COLUMN chat_message.metadata        IS '元数据(JSON)，如token用量、引用文档';
COMMENT ON COLUMN chat_message.created_at      IS '发送时间';

CREATE INDEX idx_message_conversation ON chat_message (conversation_id, created_at ASC);
