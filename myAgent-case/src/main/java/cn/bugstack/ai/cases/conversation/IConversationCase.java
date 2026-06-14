package cn.bugstack.ai.cases.conversation;

import cn.bugstack.ai.cases.model.ConversationResult;

public interface IConversationCase {

    /** 发送音频帧到 ASR */
    void sendAudioChunk(String sessionId, byte[] audioData);

    /** 结束 ASR 会话 */
    void endAsr(String sessionId);

    /** 取消会话 */
    void cancel(String sessionId);

    /** 结束全部会话（连接关闭时） */
    void endSession(String sessionId);

    /**
     * 处理文本消息：AgentFlow → TTS
     * @return 处理结果，防抖命中或输入为空时返回 null
     */
    ConversationResult processText(String sessionId, String text, Long conversationId);
}
