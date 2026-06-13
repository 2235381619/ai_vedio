package cn.bugstack.ai.domain.agent.model.entity;

public class AgentOutput {
    private String response;
    private String instruction;

    public AgentOutput() {}

    public AgentOutput(String response, String instruction) {
        this.response = response;
        this.instruction = instruction;
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
