package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.TtsRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsResponseEntity;

import java.util.function.Consumer;

public interface ITtsService {

    TtsResponseEntity synthesize(TtsRequestEntity request);

    void startSession(String sessionId);

    void endSession(String sessionId);

    void cancelSession(String sessionId);

    void streamSynthesize(TtsRequestEntity request, Consumer<byte[]> audioCallback);

}