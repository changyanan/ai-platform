package com.aiplatform.monitor.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CallLogQuery extends PageQuery {

    private String requestType;
    private Boolean success;
    private String dataSource;
    private String keyword;
    private Integer days = 7;
}
