package com.aiplatform.server.user.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    private String keyword;
    private String role;
    private Boolean enabled;
}
