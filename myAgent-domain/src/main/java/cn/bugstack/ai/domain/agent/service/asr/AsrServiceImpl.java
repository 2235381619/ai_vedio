package cn.bugstack.ai.domain.agent.service.asr;

import cn.bugstack.ai.domain.agent.model.entity.AsrRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AsrResponseEntity;
import cn.bugstack.ai.domain.agent.service.IAsrService;
import cn.bugstack.ai.domain.session.service.ISessionService;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

@Service
@Slf4j
public class AsrServiceImpl implements IAsrService {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final Map<String, Recognition> connectionMap = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AsrResponseEntity>> streamingCallbacks = new ConcurrentHashMap<>();

    private final ISessionService sessionService;

    private RecognitionParam param;

    public AsrServiceImpl(ISessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostConstruct
    public void init() {
         param = RecognitionParam.builder()
                .model("fun-asr-realtime")
                .apiKey(apiKey)
                .format("pcm")
                .sampleRate(16000)
                .build();
    }

    @Override
    public AsrResponseEntity recognize(AsrRequestEntity request) {
        String sessionId = request.getSessionId();
        log.info("开始语音识别: sessionId={}", sessionId);

        sessionService.touchSession(sessionId);
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

        try {
            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder resultText = new StringBuilder();
            double confidence = 0.0;
            List<AsrResponseEntity.WordInfo> words = new ArrayList<>();

            Recognition recognizer = new Recognition();

            ResultCallback<RecognitionResult> callback = new ResultCallback<RecognitionResult>() {
                @Override
                public void onEvent(RecognitionResult result) {
                    if (result.isSentenceEnd()) {
                        resultText.append(result.getSentence().getText());
                    }
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    log.error("ASR识别错误", e);
                    latch.countDown();
                }
            };

            recognizer.call(param, callback);

            ByteBuffer buffer = ByteBuffer.wrap(request.getAudioData());
            recognizer.sendAudioFrame(buffer);
            recognizer.stop();

            latch.await();

            return AsrResponseEntity.builder()
                    .sessionId(request.getSessionId())
                    .text(resultText.toString())
                    .confidence(confidence)
                    .words(words)
                    .isFinal(true)
                    .build();

        } catch (Exception e) {
            log.error("语音识别失败", e);
            throw new RuntimeException("语音识别失败", e);
        }
    }

    @Override
    public void startSession(String sessionId) {
        log.info("启动 ASR 会话: sessionId={}", sessionId);
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

        Recognition recognition = new Recognition();
        connectionMap.put(sessionId, recognition);
    }

    @Override
    public void endSession(String sessionId) {
        log.info("结束 ASR 会话: sessionId={}", sessionId);
        streamingCallbacks.remove(sessionId);
        sessionService.closeSession(sessionId);
        Recognition recognition = connectionMap.remove(sessionId);
        if (recognition != null) {
            try {
                recognition.stop();
                recognition.getDuplexApi().close(1000, "bye");
            } catch (Exception e) {
                log.warn("关闭ASR会话失败", e);
            }
        }
    }

    @Override
    public void cancelSession(String sessionId) {
        log.info("取消 ASR 会话: sessionId={}", sessionId);
        endSession(sessionId);
    }

    @Override
    public void startStreaming(String sessionId, Consumer<AsrResponseEntity> callback) {
        log.info("启动流式 ASR: sessionId={}", sessionId);
        streamingCallbacks.put(sessionId, callback);
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

        Recognition recognition = connectionMap.computeIfAbsent(sessionId, k -> new Recognition());
        try {
            recognition.call(param, new ResultCallback<RecognitionResult>() {
                @Override
                public void onEvent(RecognitionResult result) {
                    if (result.isSentenceEnd()) {
                        String text = result.getSentence().getText();
                        if (text != null && !text.isBlank()) {
                            log.info("ASR 流式识别到内容: sessionId={}, text={}", sessionId, text);
                            AsrResponseEntity response = AsrResponseEntity.builder()
                                    .sessionId(sessionId)
                                    .text(text)
                                    .confidence(0.0)
                                    .isFinal(result.isSentenceEnd())
                                    .build();
                            Consumer<AsrResponseEntity> cb = streamingCallbacks.get(sessionId);
                            if (cb != null) {
                                cb.accept(response);
                            }
                        }
                    }
                }

                @Override
                public void onComplete() {
                    log.debug("ASR 流式识别完成: sessionId={}", sessionId);
                }

                @Override
                public void onError(Exception e) {
                    log.error("ASR 流式识别错误: sessionId={}", sessionId, e);
                }
            });
        } catch (Exception e) {
            log.error("启动流式 ASR 失败: sessionId={}", sessionId, e);
            streamingCallbacks.remove(sessionId);
        }
    }

    @Override
    public void sendAudioChunk(String sessionId, byte[] audioData) {
        Recognition recognition = connectionMap.get(sessionId);
        if (recognition != null) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(audioData);
                recognition.sendAudioFrame(buffer);
            } catch (Exception e) {
                log.warn("发送 ASR 音频帧失败: sessionId={}", sessionId, e);
            }
        } else {
            log.warn("ASR 连接不存在，丢弃音频帧: sessionId={}", sessionId);
        }
    }

}
