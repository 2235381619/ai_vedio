package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.dto.ChunkResultDTO;
import cn.bugstack.ai.api.dto.PreviewRequestDTO;
import cn.bugstack.ai.api.dto.StrategyDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.IRagService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/rag")
public class RagController {

    private final IRagService ragService;

    public RagController(IRagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 上传文档并入库
     */
    @PostMapping("/upload")
    public Response<Void> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam("docName") String docName,
                                 @RequestParam("splitterName") String splitterName) throws IOException {
        ragService.uploadFile(file.getInputStream(), docName, splitterName);
        return Response.<Void>builder()
                .code("0000")
                .info("上传成功")
                .build();
    }

    /**
     * 预览切片效果
     */
    @PostMapping("/preview")
    public Response<List<ChunkResultDTO>> preview(@RequestBody PreviewRequestDTO request) {
        SplitterConfig config = new SplitterConfig();
        if (request.getChunkSize() != null) config.setChunkSize(request.getChunkSize());
        if (request.getOverlap() != null) config.setOverlap(request.getOverlap());
        if (request.getHeadingLevels() != null) config.setHeadingLevels(request.getHeadingLevels());
        if (request.getSimilarityThreshold() != null) config.setSimilarityThreshold(request.getSimilarityThreshold());
        if (request.getBufferSize() != null) config.setBufferSize(request.getBufferSize());
        if (request.getSeparators() != null) config.setSeparators(request.getSeparators());

        List<Document> chunks = ragService.previewChunk(request.getContent(), request.getSplitterName(), config);

        List<ChunkResultDTO> results = chunks.stream()
                .map(doc -> ChunkResultDTO.builder()
                        .content(doc.getText())
                        .metadata(doc.getMetadata())
                        .build())
                .collect(Collectors.toList());

        return Response.<List<ChunkResultDTO>>builder()
                .code("0000")
                .info("success")
                .data(results)
                .build();
    }

    /**
     * 获取所有可用切片策略
     */
    @GetMapping("/strategies")
    public Response<List<StrategyDTO>> strategies() {
        List<String> names = ragService.listStrategies();
        List<StrategyDTO> data = names.stream()
                .map(name -> StrategyDTO.builder().name(name).build())
                .collect(Collectors.toList());
        return Response.<List<StrategyDTO>>builder()
                .code("0000")
                .info("success")
                .data(data)
                .build();
    }

}
