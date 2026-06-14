package cn.bugstack.ai.domain.agent.service.asr;

import cn.bugstack.ai.domain.agent.model.entity.AsrRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AsrResponseEntity;
import cn.bugstack.ai.domain.agent.service.IAsrService;
import cn.bugstack.ai.domain.session.service.ISessionService;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Slf4j
public class AsrServiceImpl implements IAsrService {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final Map<String, Recognition> connectionMap = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AsrResponseEntity>> streamingCallbacks = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> readyLatches = new ConcurrentHashMap<>();

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

        Recognition recognition = new Recognition();
        connectionMap.put(sessionId, recognition);
    }

    @Override
    public void endSession(String sessionId) {
        log.info("结束 ASR 会话: sessionId={}", sessionId);
        streamingCallbacks.remove(sessionId);

        // 清理就绪信号，释放可能正在等待的线程
        CountDownLatch readyLatch = readyLatches.remove(sessionId);
        if (readyLatch != null) {
            readyLatch.countDown();
        }

        // 注意：这里只拆除 ASR 识别连接，不能关闭整个会话。
        // 每次说完话前端都会发 end_asr，而该次发话对应的 AI 应答+TTS 仍在异步进行，
        // 若此处关闭会话，TTS 再 touchSession 就会抛“会话已关闭”。
        // 会话的关闭统一交给 WebSocket 断开时的 ConversationCase.endSession 处理。
        Recognition recognition = connectionMap.remove(sessionId);
        if (recognition != null) {
            try {
                recognition.stop();
            } catch (Exception e) {
                log.debug("停止 ASR 时状态异常（可能未启动）: {}", e.getMessage());
            }
            try {
                recognition.getDuplexApi().close(1000, "bye");
            } catch (Exception e) {
                log.warn("关闭 ASR WebSocket 连接失败", e);
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

        // 创建就绪信号，用于等待 ASR WebSocket 连接建立
        CountDownLatch readyLatch = new CountDownLatch(1);
        readyLatches.put(sessionId, readyLatch);

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
                    readyLatch.countDown();
                    log.debug("ASR 流式识别完成: sessionId={}", sessionId);
                }

                @Override
                public void onError(Exception e) {
                    readyLatch.countDown();
                    log.error("ASR 流式识别错误: sessionId={}", sessionId, e);
                }
            });
            // call() 返回即表示 WebSocket 连接已建立、task-started 已确认（task-started 不会进入 onEvent，
            // 而 onEvent 只在收到音频后才有识别结果）。因此在此处标记就绪，避免与 sendAudioChunk 形成循环等待。
            readyLatch.countDown();
        } catch (Exception e) {
            readyLatch.countDown();
            log.error("启动流式 ASR 失败: sessionId={}", sessionId, e);
            streamingCallbacks.remove(sessionId);
        }
    }

    @Override
    public void sendAudioChunk(String sessionId, byte[] audioData) {
        // 等待 ASR WebSocket 连接就绪，避免在 idle 状态下发送音频帧
        CountDownLatch readyLatch = readyLatches.get(sessionId);
        if (readyLatch != null) {
            try {
                if (!readyLatch.await(3, TimeUnit.SECONDS)) {
                    log.warn("ASR 连接就绪超时，丢弃音频帧: sessionId={}", sessionId);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

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
