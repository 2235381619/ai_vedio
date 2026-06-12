package cn.bugstack.ai.test;

import cn.bugstack.ai.domain.agent.model.entity.TtsRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsResponseEntity;
import cn.bugstack.ai.domain.agent.service.ITtsService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@SpringBootTest
@Slf4j
public class TtsServiceTest {

    @Autowired
    private ITtsService ttsService;

    @Test
    public void testSynthesize() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        String testText = "您好，这是一个语音合成测试。欢迎使用我的语音交互功能！";
        
        log.info("测试 TTS 合成, sessionId={}, text={}", sessionId, testText);

        TtsRequestEntity request = TtsRequestEntity.builder()
                .sessionId(sessionId)
                .text(testText)
                .voice("Cherry")
                .languageType("Chinese")
                .mode("server_commit")
                .speechRate(1.0f)
                .volume(50)
                .pitchRate(1.0f)
                .build();

        TtsResponseEntity response = ttsService.synthesize(request);
        log.info("合成结果: audioLength={} bytes, firstAudioDelay={}ms", 
                response.getAudioData().length, response.getFirstAudioDelay());

        Path outputPath = Paths.get("src/test/resources/output_audio.pcm");
        Files.write(outputPath, response.getAudioData());
        log.info("音频文件已保存: {}", outputPath);

        assert response.getAudioData() != null;
        assert response.getAudioData().length > 0;
    }

    @Test
    public void testStreamSynthesize() {
        String sessionId = UUID.randomUUID().toString();
        String testText = "这是一个流式语音合成测试。";

        log.info("测试流式 TTS 合成, sessionId={}", sessionId);

        TtsRequestEntity request = TtsRequestEntity.builder()
                .sessionId(sessionId)
                .text(testText)
                .voice("Cherry")
                .languageType("Chinese")
                .mode("server_commit")
                .build();

        ttsService.streamSynthesize(request, audioChunk -> {
            log.debug("收到音频块: {} bytes", audioChunk.length);
        });
    }

    @Test
    public void testSessionManagement() {
        String sessionId = UUID.randomUUID().toString();
        
        ttsService.startSession(sessionId);
        log.info("TTS 会话已启动: {}", sessionId);

        ttsService.endSession(sessionId);
        log.info("TTS 会话已结束: {}", sessionId);
    }

}