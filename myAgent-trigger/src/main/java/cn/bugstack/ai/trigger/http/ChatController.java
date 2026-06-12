package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.dto.CreateSessionRequestDto;
import cn.bugstack.ai.api.dto.CreateSessionResponseDto;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.entity.SessionEntity;
import cn.bugstack.ai.domain.agent.service.ISessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/chat")
public class ChatController {

    private final ISessionService sessionService;

    public ChatController(ISessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/health")
    public Response<String> health() {
        return Response.<String>builder()
                .code("0000")
                .info("success")
                .data("AI Video Chat Service is running")
                .build();
    }

    @PostMapping("/session")
    public Response<CreateSessionResponseDto> createSession(@RequestBody CreateSessionRequestDto request) {
        log.info("创建会话: userId={}", request.getUserId());
        try {
            SessionEntity session = sessionService.createSession(request.getUserId(), SessionEntity.SessionType.VIDEO);
            CreateSessionResponseDto dto = CreateSessionResponseDto.builder()
                    .sessionId(session.getSessionId())
                    .createdAt(session.getCreatedAt())
                    .build();
            return Response.<CreateSessionResponseDto>builder()
                    .code("0000")
                    .info("success")
                    .data(dto)
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return Response.<CreateSessionResponseDto>builder()
                    .code("0001")
                    .info("创建会话失败: " + e.getMessage())
                    .build();
        }
    }

}
