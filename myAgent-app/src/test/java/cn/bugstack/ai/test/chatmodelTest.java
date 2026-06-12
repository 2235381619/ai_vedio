package cn.bugstack.ai.test;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class chatmodelTest {
    @Value("${spring.ai.dashscope.api-key}")
    String apikey;


    @Test
    public void test(){
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey( apikey)
                .build();

        DashScopeChatModel build = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();

        String call = build.call("你是什么模型");
        System.out.println(call);

    }
}
