package cn.bugstack.ai.cases.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationResult {
    private byte[] audioData;
    private String text;
    private Object agentType;
    private String instruction;

    public boolean isError() { return audioData == null || audioData.length == 0; }

    public static ConversationResult error(String text, Object agentType, String instruction) {
        return new ConversationResult(new byte[0], text, agentType, instruction);
    }
}
