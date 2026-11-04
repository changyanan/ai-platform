package com.aiplatform.multimodal.service;

import com.aiplatform.multimodal.vo.MultimodalTaskVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MultimodalService {

    MultimodalTaskVO process(MultipartFile file, String taskType, Long modelId, String prompt);

    List<MultimodalTaskVO> recent(int limit);

    MultimodalTaskVO get(Long id);
}
