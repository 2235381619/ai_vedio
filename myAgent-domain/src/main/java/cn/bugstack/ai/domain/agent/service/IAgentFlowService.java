package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;

public interface IAgentFlowService {
    AgentResponseEntity process(AgentRequestEntity request);
}
