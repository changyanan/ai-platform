package com.aiplatform.model.service;

import com.aiplatform.common.api.PageResult;
import com.aiplatform.model.dto.ModelConfigQuery;
import com.aiplatform.model.dto.ModelConfigRequest;
import com.aiplatform.model.vo.ModelConfigVO;
import com.aiplatform.model.vo.ModelOptionVO;
import com.aiplatform.model.vo.ModelProviderVO;
import com.aiplatform.model.vo.ModelTestResultVO;

import java.util.List;

public interface ModelConfigService {

    PageResult<ModelConfigVO> page(ModelConfigQuery query);

    List<ModelProviderVO> providers();

    List<ModelOptionVO> options();

    ModelConfigVO create(ModelConfigRequest request);

    ModelConfigVO update(Long id, ModelConfigRequest request);

    void delete(Long id);

    void setEnabled(Long id, boolean enabled);

    ModelTestResultVO testConnection(Long id);
}
