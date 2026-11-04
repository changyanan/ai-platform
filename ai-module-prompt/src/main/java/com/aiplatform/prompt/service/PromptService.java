package com.aiplatform.prompt.service;

import com.aiplatform.common.api.PageResult;
import com.aiplatform.prompt.dto.PromptQuery;
import com.aiplatform.prompt.dto.PromptRequest;
import com.aiplatform.prompt.dto.PromptTestRequest;
import com.aiplatform.prompt.vo.PromptTestResultVO;
import com.aiplatform.prompt.vo.PromptVO;
import com.aiplatform.prompt.vo.PromptVersionVO;

import java.util.List;

public interface PromptService {

    PageResult<PromptVO> page(PromptQuery query);

    PromptVO get(Long id);

    PromptVO create(PromptRequest request);

    PromptVO update(Long id, PromptRequest request);

    List<PromptVersionVO> versions(Long id);

    PromptVO publish(Long id);

    PromptVO rollback(Long id, Integer version);

    PromptTestResultVO test(Long id, PromptTestRequest request);

    List<String> categories();
}
