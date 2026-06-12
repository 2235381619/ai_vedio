package cn.bugstack.ai.domain.agent.model.valobj;

public enum AgentType {
    TEXT("text"),
    VISION("vision");

    private final String code;

    AgentType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AgentType fromCode(String code) {
        for (AgentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return TEXT;
    }
}
