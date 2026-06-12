package cn.bugstack.ai.domain.agent.service.workflow.asr;

import cn.bugstack.ai.domain.agent.model.entity.AsrRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AsrResponseEntity;

public interface IAsrService {

    AsrResponseEntity recognize(AsrRequestEntity request);

    void startSession(String sessionId);

    void endSession(String sessionId);

    void cancelSession(String sessionId);

}