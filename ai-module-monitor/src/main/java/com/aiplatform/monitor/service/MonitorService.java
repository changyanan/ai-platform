package com.aiplatform.monitor.service;

import com.aiplatform.common.api.PageResult;
import com.aiplatform.monitor.dto.CallLogQuery;
import com.aiplatform.monitor.vo.CallLogVO;
import com.aiplatform.monitor.vo.MonitorOverviewVO;

public interface MonitorService {

    MonitorOverviewVO overview(int days);

    PageResult<CallLogVO> calls(CallLogQuery query);
}
