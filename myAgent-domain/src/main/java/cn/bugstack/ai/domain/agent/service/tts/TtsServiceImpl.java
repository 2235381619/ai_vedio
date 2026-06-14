package cn.bugstack.ai.domain.agent.service.tts;

import cn.bugstack.ai.domain.agent.model.entity.TtsRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsResponseEntity;
import cn.bugstack.ai.domain.agent.service.ITtsService;
import cn.bugstack.ai.domain.session.service.ISessionService;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtime;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeCallback;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeConfig;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
@Slf4j
public class TtsServiceImpl implements ITtsService {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final Map<String, QwenTtsRealtime> connectionMap = new ConcurrentHashMap<>();

    private final ISessionService sessionService;

    private QwenTtsRealtimeParam param;

    public TtsServiceImpl(ISessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostConstruct
    public void init() {
        param = QwenTtsRealtimeParam.builder()
                .model("qwen3-tts-instruct-flash-realtime")
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                .apikey(apiKey)
                .build();
        log.info("TTS 服务初始化完成");
    }

    @Override
    public TtsResponseEntity synthesize(TtsRequestEntity request) {
        String sessionId = request.getSessionId();
        log.info("开始语音合成: sessionId={}, text={}", sessionId, request.getText());

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder audioBuilder = new StringBuilder();
        AtomicLong firstAudioDelay = new AtomicLong(0);
        long startTime = System.currentTimeMillis();

        sessionService.touchSession(sessionId);

        // 每次合成都新建连接（finish 后连接不可复用）
        QwenTtsRealtime qwenTtsRealtime = createTtsClient(latch, audioBuilder, firstAudioDelay, startTime);

        try {
            qwenTtsRealtime.connect();
            connectionMap.put(sessionId, qwenTtsRealtime);
            log.debug("新建 TTS 连接: sessionId={}", sessionId);

            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice(request.getVoice() != null ? request.getVoice() : "Cherry")
                    .languageType(request.getLanguageType() != null ? request.getLanguageType() : "Chinese")
                    .mode(request.getMode() != null ? request.getMode() : "server_commit")
                    .speechRate(request.getSpeechRate() != null ? request.getSpeechRate() : 1.0f)
                    .volume(request.getVolume() != null ? request.getVolume() : 50)
                    .pitchRate(request.getPitchRate() != null ? request.getPitchRate() : 1.0f)
                    .instructions(request.getInstruction())
                    .build();

            qwenTtsRealtime.updateSession(config);
            qwenTtsRealtime.appendText(request.getText());
            qwenTtsRealtime.finish();

            if (!latch.await(30, TimeUnit.SECONDS)) {
                log.warn("TTS 合成超时: sessionId={}", sessionId);
            }

            byte[] audioData = Base64.getMimeDecoder().decode(audioBuilder.toString().trim()
                    .replaceAll("[^A-Za-z0-9+/=]", ""));

            return TtsResponseEntity.builder()
                    .sessionId(sessionId)
                    .audioData(audioData)
                    .format("pcm")
                    .sampleRate(24000)
                    .text(request.getText())
                    .firstAudioDelay(firstAudioDelay.get())
                    .build();

        } catch (NoApiKeyException e) {
            log.error("TTS API Key 错误", e);
            connectionMap.remove(sessionId);
            throw new RuntimeException("TTS API Key 错误", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TTS 合成被中断", e);
            connectionMap.remove(sessionId);
            throw new RuntimeException("TTS 合成被中断", e);
        } catch (Exception e) {
            log.error("语音合成失败", e);
            connectionMap.remove(sessionId);
            throw new RuntimeException("语音合成失败", e);
        }
    }

    private QwenTtsRealtime createTtsClient(CountDownLatch latch, StringBuilder audioBuilder,
                                            AtomicLong firstAudioDelay, long startTime) {
        return new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
            @Override
            public void onOpen() {
                log.debug("TTS 连接已建立");
            }

            @Override
            public void onEvent(JsonObject message) {
                String type = message.get("type").getAsString();
                switch (type) {
                    case "response.audio.delta":
                        if (firstAudioDelay.get() == 0) {
                            firstAudioDelay.set(System.currentTimeMillis() - startTime);
                        }
                        String recvAudioB64 = message.get("delta").getAsString();
                        audioBuilder.append(recvAudioB64);
                        break;
                    case "response.done":
                        latch.countDown();
                        break;
                    case "session.finished":
                        latch.countDown();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onClose(int code, String reason) {
                log.debug("TTS 连接已关闭: code={}, reason={}", code, reason);
                latch.countDown();
            }
        });
    }

    @Override
    public void startSession(String sessionId) {
        log.info("启动 TTS 会话: sessionId={}", sessionId);
        try {
            QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {}

                @Override
                public void onEvent(JsonObject message) {}

                @Override
                public void onClose(int code, String reason) {}
            });

            qwenTtsRealtime.connect();

            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice("Cherry")
                    .languageType("Chinese")
                    .mode("server_commit")
                    .build();

            qwenTtsRealtime.updateSession(config);
            connectionMap.put(sessionId, qwenTtsRealtime);

        } catch (NoApiKeyException | InterruptedException e) {
            log.error("创建 TTS 会话失败", e);
            throw new RuntimeException("创建 TTS 会话失败", e);
        }
    }

    @Override
    public void endSession(String sessionId) {
        log.info("结束 TTS 会话: sessionId={}", sessionId);
        // 仅拆除 TTS 连接，不关闭会话（会话关闭统一在 WebSocket 断开时处理）
        QwenTtsRealtime tts = connectionMap.remove(sessionId);
        if (tts != null) {
            try {
                tts.finish();
                tts.close();
                log.debug("TTS 连接已关闭: sessionId={}", sessionId);
            } catch (Exception e) {
                log.warn("关闭 TTS 会话时发生错误", e);
            }
        }
    }

    @Override
    public void cancelSession(String sessionId) {
        log.info("取消 TTS 会话: sessionId={}", sessionId);
        // 中断仅取消当前 TTS 播放，不关闭会话
        QwenTtsRealtime tts = connectionMap.remove(sessionId);
        if (tts != null) {
            try {
                tts.close();
            } catch (Exception e) {
                log.warn("取消 TTS 会话时发生错误", e);
            }
        }
    }

    @Override
    public void streamSynthesize(TtsRequestEntity request, Consumer<byte[]> audioCallback) {
        String sessionId = request.getSessionId();
        log.info("开始流式语音合成: sessionId={}, text={}", sessionId, request.getText());

        sessionService.touchSession(sessionId);

        QwenTtsRealtime qwenTtsRealtime = connectionMap.get(sessionId);
        if (qwenTtsRealtime == null) {
            qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("流式 TTS 连接已建立");
                }

                @Override
                public void onEvent(JsonObject message) {
                    String type = message.get("type").getAsString();
                    if ("response.audio.delta".equals(type)) {
                        String recvAudioB64 = message.get("delta").getAsString();
                        byte[] audioData = Base64.getDecoder().decode(recvAudioB64);
                        audioCallback.accept(audioData);
                    }
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("流式 TTS 连接已关闭: code={}, reason={}", code, reason);
                }
            });

            try {
                qwenTtsRealtime.connect();
                connectionMap.put(sessionId, qwenTtsRealtime);
            } catch (Exception e) {
                log.error("建立流式 TTS 连接失败", e);
                throw new RuntimeException("建立流式 TTS 连接失败", e);
            }
        }

        try {
            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice(request.getVoice() != null ? request.getVoice() : "Cherry")
                    .languageType(request.getLanguageType() != null ? request.getLanguageType() : "Chinese")
                    .mode(request.getMode() != null ? request.getMode() : "server_commit")
                    .speechRate(request.getSpeechRate() != null ? request.getSpeechRate() : 1.0f)
                    .volume(request.getVolume() != null ? request.getVolume() : 50)
                    .pitchRate(request.getPitchRate() != null ? request.getPitchRate() : 1.0f)
                    .instructions(request.getInstruction())
                    .build();

            qwenTtsRealtime.updateSession(config);
            qwenTtsRealtime.appendText(request.getText());
            qwenTtsRealtime.finish();

        } catch (Exception e) {
            log.error("流式语音合成失败", e);
            throw new RuntimeException("流式语音合成失败", e);
        }
    }

}
