路由（LlmRoutingAgent）
在路由模式中，使用大语言模型（LLM）动态决定将请求路由到哪个子Agent。这种模式非常适合需要智能选择不同专家Agent的场景。

流程：

路由Agent接收用户输入
LLM分析输入并决定最合适的子Agent
选中的子Agent处理请求
结果返回给用户
Spring AI Alibaba LlmRoutingAgent

实现
LlmRoutingAgent 实现示例
查看完整代码
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;

// 创建专业化的子Agent
ReactAgent writerAgent = ReactAgent.builder()
.name("writer_agent")
.model(chatModel)
.description("擅长创作各类文章，包括散文、诗歌等文学作品")
.instruction("你是一个知名的作家，擅长写作和创作。请根据用户的提问进行回答。")
.outputKey("writer_output")
.build();

ReactAgent reviewerAgent = ReactAgent.builder()
.name("reviewer_agent")
.model(chatModel)
.description("擅长对文章进行评论、修改和润色")
.instruction("你是一个知名的评论家，擅长对文章进行评论和修改。" +
"对于散文类文章，请确保文章中必须包含对于西湖风景的描述。")
.outputKey("reviewer_output")
.build();

ReactAgent translatorAgent = ReactAgent.builder()
.name("translator_agent")
.model(chatModel)
.description("擅长将文章翻译成各种语言")
.instruction("你是一个专业的翻译家，能够准确地将文章翻译成目标语言。")
.outputKey("translator_output")
.build();

// 创建路由Agent
LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
.name("content_routing_agent")
.description("根据用户需求智能路由到合适的专家Agent")
.model(chatModel)
.subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
.build();

// 使用 - LLM会自动选择最合适的Agent
Optional<OverAllState> result1 = routingAgent.invoke("帮我写一篇关于春天的散文");
// LLM会路由到 writerAgent

Optional<OverAllState> result2 = routingAgent.invoke("请帮我修改这篇文章：春天来了，花开了。");
// LLM会路由到 reviewerAgent

Optional<OverAllState> result3 = routingAgent.invoke("请将以下内容翻译成英文：春暖花开");
// LLM会路由到 translatorAgent

关键特性
智能路由：LLM根据输入内容和子Agent的描述自动选择最合适的Agent
灵活扩展：可以轻松添加新的专家Agent，LLM会自动识别并路由
描述驱动：子Agent的 description 非常重要，它告诉LLM何时应该选择该Agent
单次执行：每次请求只路由到一个Agent执行
优化路由准确性
为了提高路由的准确性，需要注意以下几点：

优化路由准确性示例
查看完整代码
// 1. 提供清晰明确的Agent描述
ReactAgent codeAgent = ReactAgent.builder()
.name("code_agent")
.model(chatModel)
.description("专门处理编程相关问题，包括代码编写、调试、重构和优化。" +
"擅长Java、Python、JavaScript等主流编程语言。")
.instruction("你是一个资深的软件工程师...")
.build();

// 2. 明确Agent的职责边界
ReactAgent businessAgent = ReactAgent.builder()
.name("business_agent")
.model(chatModel)
.description("专门处理商业分析、市场研究和战略规划问题。" +
"不处理技术实现细节。")
.instruction("你是一个资深的商业分析师...")
.build();

// 3. 使用不同领域的Agent避免重叠
LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
.name("multi_domain_router")
.model(chatModel)
.subAgents(List.of(codeAgent, businessAgent, writerAgent))
.build();

自定义系统提示和指令
LlmRoutingAgent 支持通过 systemPrompt 和 instruction 来自定义路由决策行为，提供更精确的路由控制。

使用 SystemPrompt
systemPrompt 用于设置路由决策的系统提示，会替换默认的系统提示。你可以通过它提供详细的决策规则和上下文：

LlmRoutingAgent 自定义系统提示示例
final String ROUTING_SYSTEM_PROMPT = """
你是一个智能的内容路由Agent，负责根据用户需求将任务路由到最合适的专家Agent。

## 你的职责
1. 仔细分析用户输入的意图和需求
2. 根据任务特性，选择最合适的专家Agent
3. 确保路由决策准确、高效

## 可用的子Agent及其职责

### writer_agent
- **功能**: 擅长创作各类文章，包括散文、诗歌等文学作品
- **适用场景**:
* 用户需要创作新文章、散文、诗歌等原创内容
* 简单的写作任务
- **输出**: writer_output

### reviewer_agent
- **功能**: 擅长对文章进行评论、修改和润色
- **适用场景**:
* 用户需要修改、评审或优化现有文章
* 需要提高文章质量
- **输出**: reviewer_output

### translator_agent
- **功能**: 擅长将文章翻译成各种语言
- **适用场景**:
* 用户需要将内容翻译成其他语言
* 多语言转换需求
- **输出**: translator_output

## 决策规则

1. **写作任务**: 如果用户需要创作新内容，选择 writer_agent
2. **修改任务**: 如果用户需要修改或优化现有内容，选择 reviewer_agent
3. **翻译任务**: 如果用户需要翻译内容，选择 translator_agent

## 响应格式
只返回Agent名称（writer_agent、reviewer_agent、translator_agent），不要包含其他解释。
""";

LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
.name("content_routing_agent")
.description("根据用户需求智能路由到合适的专家Agent")
.model(chatModel)
.systemPrompt(ROUTING_SYSTEM_PROMPT)
.subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
.build();

使用 Instruction
instruction 用于设置路由决策的用户指令，会作为 UserMessage 添加到消息列表中。你可以通过它提供额外的上下文信息或特定的路由指导：

LlmRoutingAgent 使用指令示例
// 使用 instruction 提供额外的路由指导
final String ROUTING_INSTRUCTION = """
请根据用户的需求，选择最合适的Agent来处理任务。

特别注意：
- 如果用户明确提到"写"、"创作"、"生成"等词汇，优先选择 writer_agent
- 如果用户提到"修改"、"优化"、"评审"等词汇，选择 reviewer_agent
- 如果用户提到"翻译"、"转换语言"等词汇，选择 translator_agent
  """;

LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
.name("content_routing_agent")
.description("根据用户需求智能路由到合适的专家Agent")
.model(chatModel)
.instruction(ROUTING_INSTRUCTION)
.subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
.build();

同时使用 SystemPrompt 和 Instruction
你可以同时使用 systemPrompt 和 instruction 来提供更完整的路由决策上下文：

LlmRoutingAgent 同时使用 SystemPrompt 和 Instruction 示例
final String ROUTING_SYSTEM_PROMPT = """
你是一个智能的内容路由Agent，负责根据用户需求将任务路由到最合适的专家Agent。

## 可用的子Agent及其职责

### writer_agent
- **功能**: 擅长创作各类文章
- **输出**: writer_output

### reviewer_agent
- **功能**: 擅长对文章进行评论、修改和润色
- **输出**: reviewer_output

### translator_agent
- **功能**: 擅长将文章翻译成各种语言
- **输出**: translator_output

## 响应格式
只返回Agent名称，不要包含其他解释。
""";

final String ROUTING_INSTRUCTION = """
请仔细分析用户输入，根据以下规则选择最合适的Agent：
1. 创作新内容 -> writer_agent
2. 修改现有内容 -> reviewer_agent
3. 翻译内容 -> translator_agent
   """;

LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
.name("content_routing_agent")
.description("根据用户需求智能路由到合适的专家Agent")
.model(chatModel)
.systemPrompt(ROUTING_SYSTEM_PROMPT)
.instruction(ROUTING_INSTRUCTION)
.subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
.build();

SystemPrompt 和 Instruction 的区别
特性	SystemPrompt	Instruction
作用位置	系统消息（SystemMessage）	用户消息（UserMessage）
用途	定义路由Agent的角色、职责和决策规则	提供具体的路由指导或额外上下文
优先级	更高，影响整体路由行为	作为补充信息
使用场景	需要详细定义路由规则和Agent职责时	需要提供特定场景的路由指导时
💡 提示：

使用 systemPrompt 来定义路由Agent的整体行为和决策框架
使用 instruction 来提供特定场景的路由指导或额外上下文
两者可以配合使用，提供更精确的路由控制
监督者（SupervisorAgent）
在监督者模式中，使用大语言模型（LLM）作为监督者，动态决定将任务路由到哪个子Agent，并支持多步骤循环路由。与 LlmRoutingAgent 不同，SupervisorAgent 支持子Agent执行完成后返回监督者，监督者可以根据执行结果继续路由到其他Agent或完成任务。

流程：

监督者Agent接收用户输入或前序Agent的输出
LLM分析当前状态并决定最合适的子Agent
选中的子Agent处理任务
子Agent执行完成后返回监督者
监督者根据结果决定：
继续路由到另一个子Agent（多步骤任务）
返回 FINISH 完成任务
Spring AI Alibaba SupervisorAgent

实现
SupervisorAgent 基础示例
查看完整代码
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;

// 创建专业化的子Agent
ReactAgent writerAgent = ReactAgent.builder()
.name("writer_agent")
.model(chatModel)
.description("擅长创作各类文章，包括散文、诗歌等文学作品")
.instruction("你是一个知名的作家，擅长写作和创作。请根据用户的提问进行回答。")
.outputKey("writer_output")
.build();

ReactAgent translatorAgent = ReactAgent.builder()
.name("translator_agent")
.model(chatModel)
.description("擅长将文章翻译成各种语言")
.instruction("你是一个专业的翻译家，能够准确地将文章翻译成目标语言。")
.outputKey("translator_output")
.build();

// 创建监督者Agent
SupervisorAgent supervisorAgent = SupervisorAgent.builder()
.name("content_supervisor")
.description("内容管理监督者，负责协调写作、翻译等任务")
.model(chatModel)
.subAgents(List.of(writerAgent, translatorAgent))
.build();

// 使用 - 监督者会根据任务自动路由并支持多步骤处理
Optional<OverAllState> result = supervisorAgent.invoke("帮我写一篇关于春天的短文");

自定义系统提示
你可以通过 systemPrompt 为监督者提供详细的决策规则和上下文：

SupervisorAgent 自定义系统提示示例
查看完整代码
final String SUPERVISOR_SYSTEM_PROMPT = """
你是一个智能的内容管理监督者，负责协调和管理多个专业Agent来完成用户的内容处理需求。

## 你的职责
1. 分析用户需求，将其分解为合适的子任务
2. 根据任务特性，选择合适的Agent进行处理
3. 监控任务执行状态，决定是否需要继续处理或完成任务
4. 当所有任务完成时，返回FINISH结束流程

## 可用的子Agent及其职责

### writer_agent
- **功能**: 擅长创作各类文章，包括散文、诗歌等文学作品
- **适用场景**:
* 用户需要创作新文章、散文、诗歌等原创内容
* 简单的写作任务，不需要后续评审或修改
- **输出**: writer_output

### translator_agent
- **功能**: 擅长将文章翻译成各种语言
- **适用场景**: 当文章需要翻译成其他语言时
- **输出**: translator_output

## 决策规则

1. **单一任务判断**:
- 如果用户只需要简单写作，选择 writer_agent
- 如果用户需要翻译，选择 translator_agent

2. **多步骤任务处理**:
- 如果用户需求包含多个步骤（如"先写文章，然后翻译"），需要分步处理
- 先路由到第一个合适的Agent，等待其完成
- 完成后，根据剩余需求继续路由到下一个Agent
- 直到所有步骤完成，返回FINISH

3. **任务完成判断**:
- 当用户的所有需求都已满足时，返回FINISH

## 响应格式
只返回Agent名称（writer_agent、translator_agent）或FINISH，不要包含其他解释。
""";

SupervisorAgent supervisorAgent = SupervisorAgent.builder()
.name("content_supervisor")
.description("内容管理监督者")
.model(chatModel)
.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
.subAgents(List.of(writerAgent, translatorAgent))
.build();

使用 Instruction 占位符
SupervisorAgent 支持通过 instruction 使用占位符来读取前序Agent的输出，这在 SupervisorAgent 作为 SequentialAgent 的子Agent时特别有用：

SupervisorAgent 使用占位符示例
查看完整代码
// 第一个Agent：写文章
ReactAgent articleWriterAgent = ReactAgent.builder()
.name("article_writer")
.model(chatModel)
.description("专业写作Agent，负责创作文章")
.instruction("你是一个知名的作家，擅长写作和创作。请根据用户的提问进行回答：{input}。")
.outputKey("article_content")
.build();

// 监督者的子Agent
ReactAgent translatorAgent = ReactAgent.builder()
.name("translator_agent")
.model(chatModel)
.description("擅长将文章翻译成各种语言")
.instruction("你是一个专业的翻译家，能够准确地将文章翻译成目标语言。待翻译文章：

{article_content}。")
.outputKey("translator_output")
.build();

ReactAgent reviewerAgent = ReactAgent.builder()
.name("reviewer_agent")
.model(chatModel)
.description("擅长对文章进行评审和修改")
.instruction("你是一个知名的评论家，擅长对文章进行评论和修改。待评审文章：

{article_content}。")
.outputKey("reviewer_output")
.build();

// 监督者的instruction使用占位符读取前序Agent的输出
final String SUPERVISOR_INSTRUCTION = """
你是一个智能的内容处理监督者，你可以看到前序Agent的聊天历史与任务处理记录。当前，你收到了以下文章内容：

{article_content}

请根据文章内容的特点，决定是进行翻译还是评审：
- 如果文章是中文且需要翻译，选择 translator_agent
- 如果文章需要评审和改进，选择 reviewer_agent
- 如果任务完成，返回 FINISH
  """;

final String SUPERVISOR_SYSTEM_PROMPT = """
你是一个智能的内容处理监督者，负责协调翻译和评审任务。

## 可用的子Agent及其职责

### translator_agent
- **功能**: 擅长将文章翻译成各种语言
- **输出**: translator_output

### reviewer_agent
- **功能**: 擅长对文章进行评审和修改
- **输出**: reviewer_output

## 响应格式
只返回Agent名称（translator_agent、reviewer_agent）或FINISH，不要包含其他解释。
""";

// 创建SupervisorAgent，instruction中包含占位符
SupervisorAgent supervisorAgent = SupervisorAgent.builder()
.name("content_supervisor")
.description("内容处理监督者，根据前序Agent的输出决定翻译或评审")
.model(chatModel)
.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
.instruction(SUPERVISOR_INSTRUCTION)
.subAgents(List.of(translatorAgent, reviewerAgent))
.build();

// 创建SequentialAgent，SupervisorAgent作为子Agent
SequentialAgent sequentialAgent = SequentialAgent.builder()
.name("content_processing_workflow")
.description("内容处理工作流：先写文章，然后根据文章内容决定翻译或评审")
.subAgents(List.of(articleWriterAgent, supervisorAgent))
.build();

// 使用
Optional<OverAllState> result = sequentialAgent.invoke("帮我写一篇关于春天的短文，然后翻译成英文");

关键特性
多步骤循环路由：子Agent执行完成后会返回监督者，监督者可以继续路由到其他Agent，实现多步骤任务处理
智能决策：使用LLM分析当前状态和任务需求，动态选择最合适的子Agent
Instruction占位符支持：instruction 支持使用占位符（如 {article_content}）读取前序Agent的输出
自定义系统提示：通过 systemPrompt 提供详细的决策规则和上下文
自动重试机制：内置重试机制（最多2次），确保路由决策的可靠性
任务完成控制：监督者可以返回 FINISH 来结束任务流程
与 LlmRoutingAgent 的区别
特性	LlmRoutingAgent	SupervisorAgent
路由次数	单次路由	支持多步骤循环路由
子Agent返回	直接结束	返回监督者继续决策
多步骤任务	❌ 不支持	✅ 支持
Instruction占位符	❌ 不支持	✅ 支持
适用场景	简单的单次路由	复杂的多步骤任务编排
最佳实践
清晰的系统提示：提供详细的决策规则和子Agent职责描述，帮助LLM做出准确的路由决策
利用占位符：在 instruction 中使用占位符读取前序Agent的输出，实现上下文感知的路由
明确的输出键：为每个子Agent设置有意义的 outputKey，便于后续Agent引用
任务分解：将复杂任务分解为多个步骤，让监督者逐步协调完成
嵌套使用：可以将 SupervisorAgent 作为 SequentialAgent 的子Agent，实现更复杂的工作流
💡 提示：SupervisorAgent 特别适合需要多步骤任务编排的场景，例如"先写文章，然后翻译，最后评审"这样的复杂工作流。