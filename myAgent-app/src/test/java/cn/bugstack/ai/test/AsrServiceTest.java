package cn.bugstack.ai.test;

import cn.bugstack.ai.domain.agent.model.entity.AsrRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AsrResponseEntity;
import cn.bugstack.ai.domain.agent.service.IAsrService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class AsrServiceTest {

    @Autowired
    private IAsrService asrService;

    @Test
    public void testRecognize() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        log.info("测试 ASR 识别, sessionId={}", sessionId);

        Path testAudioPath = Paths.get("src/test/resources/output_audio.pcm");
        byte[] audioData = Files.readAllBytes(testAudioPath);

        AsrRequestEntity request = AsrRequestEntity.builder()
                .sessionId(sessionId)
                .audioData(audioData)
                .build();

        AsrResponseEntity response = asrService.recognize(request);
        log.info("识别结果: text={}, confidence={}", response.getText(), response.getConfidence());

        assert response.getText() != null;
        assert !response.getText().isEmpty();
    }

    @Test
    public void testSessionManagement() {
        String sessionId = UUID.randomUUID().toString();
        
        asrService.startSession(sessionId);
        log.info("ASR 会话已启动: {}", sessionId);

        asrService.endSession(sessionId);
        log.info("ASR 会话已结束: {}", sessionId);
    }

}