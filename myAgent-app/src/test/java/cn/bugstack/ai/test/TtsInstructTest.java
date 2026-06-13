package cn.bugstack.ai.test;

import cn.bugstack.ai.domain.agent.model.entity.TtsRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsResponseEntity;
import cn.bugstack.ai.domain.agent.service.ITtsService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class TtsInstructTest {

    @Autowired
    private ITtsService ttsService;

    private void playPcm(byte[] audioData) throws Exception {
        AudioFormat format = new AudioFormat(24000, 16, 1, true, false);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
        line.open(format);
        line.start();
        line.write(audioData, 0, audioData.length);
        line.drain();
        line.close();
    }

    @Test
    public void testHappyTone() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String testText = "太棒了！今天真是一个好天气，我们一起去公园散步吧！";

        log.info("测试开心语气");
        TtsRequestEntity request = TtsRequestEntity.builder()
                .sessionId(sessionId).text(testText)
                .voice("Cherry").languageType("Chinese").mode("server_commit")
                .speechRate(1.0f).volume(50).pitchRate(1.0f)
                .instruction("用开心兴奋的语气朗读，声音活泼一些")
                .build();
        TtsResponseEntity response = ttsService.synthesize(request);
        log.info("合成完成: {} bytes，开始播放", response.getAudioData().length);
        playPcm(response.getAudioData());
    }

    @Test
    public void testSadTone() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String testText = "听到这个消息，我真的很遗憾。希望一切都能好起来";

        log.info("测试悲伤语气");
        TtsRequestEntity request = TtsRequestEntity.builder()
                .sessionId(sessionId).text(testText)
                .voice("Cherry").languageType("Chinese").mode("server_commit")
                .speechRate(1.0f).volume(45).pitchRate(1.0f)
                .instruction("用低沉悲伤的语气缓缓朗读")
                .build();
        TtsResponseEntity response = ttsService.synthesize(request);
        log.info("合成完成: {} bytes，开始播放", response.getAudioData().length);
        playPcm(response.getAudioData());
    }

    @Test
    public void testNormalTone() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String testText = "您好，请问有什么可以帮助您的？";

        log.info("测试正常语气");
        TtsRequestEntity request = TtsRequestEntity.builder()
                .sessionId(sessionId).text(testText)
                .voice("Cherry").languageType("Chinese").mode("server_commit")
                .speechRate(1.0f).volume(45).pitchRate(0.9f)
                .instruction("用低沉悲伤的语气缓缓朗读")
                .build();
        TtsResponseEntity response = ttsService.synthesize(request);
        log.info("合成完成: {} bytes，开始播放", response.getAudioData().length);
        playPcm(response.getAudioData());
    }
}
