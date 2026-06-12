# Bug: @Tool 方法参数 sessionId 被 AI 模型乱填

## 问题描述

VisionTool 的 `captureCameraFrame(String sessionId)` 方法标注了 `@Tool` 注解，AI 模型在调用该工具时会自行生成 `sessionId` 参数值，而不是使用真实的会话 ID。

### 日志表现

```
VisionTool - Agent 调用截帧工具: sessionId=identify_object_in_hand
```

模型将参数名 `sessionId` 理解为"要识别的东西"，填入了 `identify_object_in_hand`，导致 WebSocket 会话查找失败：

```
WebSocketSessionManager - WebSocket 会话不存在或已关闭: sessionId=identify_object_in_hand
```

## 原因

`@Tool` 注解的方法参数对 AI 模型完全透明，模型根据参数名和类型自行决定填入什么值。对于业务相关的上下文参数（如 sessionId、userId 等），模型无法获取真实值，会凭空编造。

## 解决方案

1. 创建 `SessionContextHolder`，通过 ThreadLocal 在调用链中传递 sessionId
2. `AgentFlowServiceImpl.process()` 调用 `supervisorAgent.invoke()` 前设置 sessionId，finally 中清理
3. VisionTool 的 `captureCameraFrame()` 改为无参方法，从 `SessionContextHolder.getSessionId()` 获取

### 新增文件

- `domain/.../model/valobj/SessionContextHolder.java` — ThreadLocal 持有者

### 修改文件

- `app/.../tools/VisionTool.java` — 移除 sessionId 参数，改为从 ThreadLocal 获取
- `domain/.../workflow/agent/AgentFlowServiceImpl.java` — 调用 Agent 前设置上下文

## 经验教训

`@Tool` 方法只应暴露 AI 模型**需要决策**的参数（如"是否截帧"），不应暴露**业务上下文**参数。业务上下文应通过 ThreadLocal/请求上下文隐式传递。
