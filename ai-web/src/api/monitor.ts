import type { PageResult } from '@/types/api'
import type { MonitorCallLog, MonitorCallQuery, MonitorOverview } from '@/types/monitor'
import { http } from '@/utils/http'

export function fetchMonitorOverview(days = 7): Promise<MonitorOverview> {
  return http<MonitorOverview>({
    url: '/admin/monitor/overview',
    method: 'get',
    params: { days },
  })
}

export function fetchMonitorCalls(query: MonitorCallQuery): Promise<PageResult<MonitorCallLog>> {
  return http<PageResult<MonitorCallLog>>({
    url: '/admin/monitor/calls',
    method: 'get',
    params: query,
  })
}
