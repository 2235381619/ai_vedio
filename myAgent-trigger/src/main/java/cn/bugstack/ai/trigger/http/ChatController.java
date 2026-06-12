package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.dto.CreateSessionRequestDto;
import cn.bugstack.ai.api.dto.CreateSessionResponseDto;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.adapter.repository.IChatRepository;
import cn.bugstack.ai.domain.agent.model.entity.ChatConversationEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatMessageEntity;
import cn.bugstack.ai.domain.agent.model.entity.SessionEntity;
import cn.bugstack.ai.domain.agent.service.ISessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/chat")
public class ChatController {

    private final ISessionService sessionService;
    private final IChatRepository chatRepository;

    public ChatController(ISessionService sessionService, IChatRepository chatRepository) {
        this.sessionService = sessionService;
        this.chatRepository = chatRepository;
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

    // ===== 对话管理（聊天记录持久化）=====

    @GetMapping("/conversations")
    public Response<List<ChatConversationEntity>> getConversations(
            @RequestParam(defaultValue = "default") String userId) {
        try {
            List<ChatConversationEntity> list = chatRepository.getUserConversations(userId);
            return Response.<List<ChatConversationEntity>>builder()
                    .code("0000").info("success").data(list).build();
        } catch (Exception e) {
            log.error("获取对话列表失败", e);
            return Response.<List<ChatConversationEntity>>builder()
                    .code("0001").info("获取对话列表失败: " + e.getMessage()).build();
        }
    }

    @PostMapping("/conversations")
    public Response<Map<String, Object>> createConversation(
            @RequestParam(defaultValue = "default") String userId) {
        try {
            Long id = chatRepository.createConversation(userId, "新对话");
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            return Response.<Map<String, Object>>builder()
                    .code("0000").info("success").data(data).build();
        } catch (Exception e) {
            log.error("创建对话失败", e);
            return Response.<Map<String, Object>>builder()
                    .code("0001").info("创建对话失败: " + e.getMessage()).build();
        }
    }

    @DeleteMapping("/conversations/{id}")
    public Response<Void> deleteConversation(@PathVariable Long id) {
        try {
            chatRepository.deleteConversation(id);
            return Response.<Void>builder()
                    .code("0000").info("success").build();
        } catch (Exception e) {
            log.error("删除对话失败", e);
            return Response.<Void>builder()
                    .code("0001").info("删除对话失败: " + e.getMessage()).build();
        }
    }

    @GetMapping("/conversations/{id}/messages")
    public Response<List<ChatMessageEntity>> getMessages(@PathVariable Long id) {
        try {
            List<ChatMessageEntity> messages = chatRepository.getConversationMessages(id);
            return Response.<List<ChatMessageEntity>>builder()
                    .code("0000").info("success").data(messages).build();
        } catch (Exception e) {
            log.error("获取消息失败", e);
            return Response.<List<ChatMessageEntity>>builder()
                    .code("0001").info("获取消息失败: " + e.getMessage()).build();
        }
    }
}
