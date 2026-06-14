# AI 视频语音对话助手

基于 Spring AI Alibaba + DashScope 多模态大模型的实时视频语音对话助手。

## 项目概述

打开摄像头与麦克风，让 AI 能够看到摄像头中的视频内容、听到用户说的话，并给予恰当的语音回应。综合考虑视觉内容的理解准确性、语音交互的自然度与流畅性，以及端云协同的成本控制策略。

---

## 一、用户故事

### 计划实现的用户故事

| 用户故事 | 描述 | 优先级 |
|---------|------|--------|
| 语音对话 | 用户通过麦克风说话，AI 语音回复 | P0 |
| 视觉理解 | AI 能看到摄像头画面并回答问题 | P0 |
| 多轮对话 | 同一会话中连续对话，AI 记住上下文 | P0 |
| 对话记录 | 历史对话持久化，前端可查看 | P0 |
| 语气控制 | AI 根据场景使用不同语气朗读 | P1 |
| 多会话管理 | 前端新建、切换、删除会话 | P1 |
| 对话标题自动生成 | 根据首条消息自动命名会话 | P1 |
| 成本控制 | 路由前置过滤、图片压缩 | P1 |
| 对话打断 | 用户可中断 AI 当前回复 | P2 |
| 端云协同 | 本地预处理 + 云端推理 | P2 |

### 实际实现的用户故事

| 用户故事 | 实现情况 |
|---------|---------|
| **语音对话** | ✅ WebSocket 实时传音频 → ASR（阿里云语音识别）→ Agent 处理 → TTS（语音合成）→ 播放 |
| **视觉理解** | ✅ 摄像头采集 → JPEG 压缩 → Vision Node（qwen-vl-max）→ 理解画面并回答 |
| **多轮对话** | ✅ DB 持久化消息 → 每次请求加载历史 → 构造 Message 列表传入 LLM |
| **对话记录** | ✅ chat_message 表存储 → REST API 查询 → 前端展示 |
| **语气控制** | ✅ Agent 输出 JSON 包含 `instruction` 字段 → TTS 按语气合成 |
| **多会话管理** | ✅ 新建、切换、软删除对话 → 前端对话列表 |
| **对话标题自动生成** | ✅ 首条用户消息自动设为对话标题 |
| **成本控制** | ✅ 路由前置过滤（text/vision 分流）+ 图片压缩（JPEG 0.5）均已采用 |
| **对话打断** | ❌ 暂未实现 |
| **端云协同** | ❌ 暂未实现 |

---

## 二、架构设计

### 整体架构

```
前端 (Browser)
  ├─ WebSocket ←→ 后端
  │   ├─ 音频上行 → ASR
  │   ├─ 音频下行 ← TTS
  │   ├─ 视频上行 → Camera Frame
  │   └─ 控制消息
  ├─ REST API
  │   ├─ /api/chat/conversations ← 对话列表
  │   ├─ /api/chat/conversations/{id}/messages ← 历史消息
  │   └─ /api/chat/session ← 创建会话
  └─ 本地摄像头 + 麦克风采集

后端 (Spring Boot)
  ├─ SpeechWebSocketHandler → 消息路由
  ├─ AgentFlow (StateGraph)
  │   ├─ RouterNode → text / vision 分流
  │   ├─ TextAgent (ReactAgent) → 纯文本对话
  │   └─ VisionNode → 多模态理解（看图 + 对话）
  ├─ ASR Service → 阿里云语音识别
  ├─ TTS Service → 阿里云语音合成
  └─ Chat Repository → PostgreSQL 持久化

数据库
  ├─ chat_conversation → 对话记录
  └─ chat_message → 消息内容（role, content, metadata）
```

### 核心工作流

```
用户说话 → ASR 流式识别
         → AgentFlowServiceImpl.process()
             1. 从 DB 加载历史消息 → 构造 Message 列表
             2. RouterNode 判断路由
                ├─ text → TextAgent（qwen-plus，JSON 格式输出）
                └─ vision → VisionNode（qwen-vl-max，图文理解 + JSON 输出）
             3. 解析 JSON 响应（response + instruction）
             4. 持久化到 DB
             5. 返回
         → TTS 语音合成（按 instruction 控制语气）
         → 音频推送到前端播放
```

---

## 三、成本控制策略

### 思考过的策略

| 策略 | 思路 | 是否采用 |
|------|------|---------|
| **路由前置过滤** | 先判断是否需要看图，不需要则走便宜的 text 模型 | ✅ 已采用 |
| **图片压缩** | 降低分辨率/JPEG 质量，减小传输和模型处理开销 | ✅ 已采用（质量 0.5） |
| **Token 用量监控** | 记录每次调用 token，设置预算阈值 | ❌ 未采用 |
| **本地预筛选** | 前端判断画面变化幅度，无变化不上传 | ❌ 未采用 |
| **ASR 闲时降噪** | 无语音输入时降低采样率 | ❌ 未采用 |

### 实际采用的策略

1. **路由前置过滤**
   - RouterNode 先判断用户输入是否需要视觉信息
   - 纯文本对话走 qwen-plus（更便宜），只有需要看图才走 qwen-vl-max
   - 减少不必要的多模态模型调用

2. **图像压缩**
   - 摄像头帧在前端压缩为 JPEG，质量 0.5
   - 平衡清晰度与传输带宽

---

## 四、技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| AI 模型 | DashScope (阿里云) | 通义系列：qwen-plus（文本）、qwen-vl-max（多模态） |
| 语音识别 | 阿里云 ASR | 流式识别，实时转写 |
| 语音合成 | 阿里云 TTS | 支持语气控制 |
| 工作流引擎 | Spring AI Alibaba Graph | StateGraph + ReactAgent 编排 |
| 数据库 | PostgreSQL | 对话记录持久化 |
| WebSocket | Spring WebSocket | 实时音视频传输 |
| 构建工具 | Maven + Spring Boot 3 | |

---

## 五、项目结构

```
myAgent/
├── myAgent-api           # API 接口定义 + DTO
├── myAgent-app           # Spring Boot 主应用
│   ├── config/           # 配置类（AgentFlow、WebSocket、线程池）
│   ├── tools/            # 工具类（VisionTool）
│   └── resources/
│       ├── static/       # 前端页面（chat.html）
│       └── mybatis/      # MyBatis Mapper XML
├── myAgent-domain        # 领域层
│   ├── model/            # 实体 + 值对象
│   ├── adapter/          # 仓储接口
│   └── service/          # 领域服务（AgentFlow、ASR、TTS、Session）
├── myAgent-trigger       # 触发器层（HTTP Controller）
├── myAgent-infrastructure # 基础设施层（MyBatis DAO + Repository 实现）
└── myAgent-types         # 通用类型定义
```

---

## 六、启动方式

```bash
# 1. 配置 application-dev.yml 中的阿里云 API Key
# 2. 创建 PostgreSQL 数据库并执行建表脚本
# 3. 编译启动
mvn install -DskipTests
cd myAgent-app
mvn spring-boot:run
# 4. 打开浏览器
# http://localhost:8091/chat.html
```

---

> 基于 DDD 脚手架 [xfg-frame-archetype](https://bugstack.cn) 构建
