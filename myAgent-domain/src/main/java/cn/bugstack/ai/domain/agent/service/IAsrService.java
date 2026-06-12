package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.AsrRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AsrResponseEntity;

import java.util.function.Consumer;

public interface IAsrService {
    AsrResponseEntity recognize(AsrRequestEntity request);
    void startStreaming(String sessionId, Consumer<AsrResponseEntity> callback);
    void sendAudioChunk(String sessionId, byte[] audioData);
    void startSession(String sessionId);
    void endSession(String sessionId);
    void cancelSession(String sessionId);
}
