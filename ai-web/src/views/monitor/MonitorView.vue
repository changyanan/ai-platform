<script setup lang="ts">
import {
  ChatDotRound,
  Coin,
  Collection,
  Cpu,
  DataLine,
  Document,
  MagicStick,
  Picture,
  Refresh,
  Search,
  SetUp,
  Timer,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  BarChart,
  LineChart,
  PieChart,
} from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import { fetchMonitorCalls, fetchMonitorOverview } from '@/api/monitor'
import PageHeader from '@/components/PageHeader.vue'
import type {
  MonitorCallLog,
  MonitorOverview,
  MonitorSummary,
} from '@/types/monitor'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer,
])

const loading = ref(false)
const callsLoading = ref(false)
const days = ref(7)
const overview = ref<MonitorOverview | null>(null)
const calls = ref<MonitorCallLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const filters = reactive<{
  requestType: string
  success: '' | 'true' | 'false'
  dataSource: '' | 'REAL' | 'SIMULATED'
  keyword: string
}>({
  requestType: '',
  success: '',
  dataSource: '',
  keyword: '',
})

const trendChartRef = ref<HTMLDivElement>()
const typeChartRef = ref<HTMLDivElement>()
const modelChartRef = ref<HTMLDivElement>()
let trendChart: ReturnType<typeof echarts.init> | null = null
let typeChart: ReturnType<typeof echarts.init> | null = null
let modelChart: ReturnType<typeof echarts.init> | null = null

const typeLabels: Record<string, string> = {
  CHAT: '对话',
  AGENT: 'Agent',
  RAG_ANSWER: 'RAG 问答',
  RAG_EMBEDDING: '向量化',
  VISION: '视觉',
  ASR: '语音',
  DOCUMENT_ANALYSIS: '文档分析',
  PROMPT_TEST: 'Prompt 测试',
  CONNECTIVITY: '连通测试',
  EMBEDDING: '向量测试',
}

const requestTypes = [
  'CHAT',
  'AGENT',
  'RAG_ANSWER',
  'RAG_EMBEDDING',
  'VISION',
  'ASR',
  'DOCUMENT_ANALYSIS',
  'PROMPT_TEST',
  'CONNECTIVITY',
  'EMBEDDING',
]

const summary = computed<MonitorSummary | null>(() => overview.value?.summary ?? null)

const hasSimulated = computed(() => (summary.value?.simulatedCalls ?? 0) > 0)

const latestCall = computed(() => calls.value[0])

function typeLabel(type: string): string {
  return typeLabels[type] ?? type
}

function formatNumber(value?: number): string {
  if (value === undefined || value === null) return '—'
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatLatency(value?: number): string {
  if (value === undefined || value === null) return '—'
  return value >= 1000 ? `${(value / 1000).toFixed(2)}s` : `${value}ms`
}

function formatToken(value?: number): string {
  return value === undefined || value === null ? '—' : formatNumber(value)
}

function formatCost(value?: number): string {
  if (value === undefined || value === null) return '未知'
  return `¥ ${Number(value).toFixed(4)}`
}

function statusLabel(success: boolean): string {
  return success ? '成功' : '失败'
}

function sourceLabel(source: string): string {
  return source === 'SIMULATED' ? 'SIMULATED' : 'REAL'
}

function buildCallQuery() {
  return {
    page: page.value,
    size: size.value,
    days: days.value,
    requestType: filters.requestType || undefined,
    success: filters.success === '' ? undefined : filters.success === 'true',
    dataSource: filters.dataSource || undefined,
    keyword: filters.keyword.trim() || undefined,
  }
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await fetchMonitorOverview(days.value)
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

async function loadCalls() {
  callsLoading.value = true
  try {
    const result = await fetchMonitorCalls(buildCallQuery())
    calls.value = result.records
    total.value = result.total
  } finally {
    callsLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadCalls()])
}

function handleSearch() {
  page.value = 1
  void loadCalls()
}

function resetFilters() {
  filters.requestType = ''
  filters.success = ''
  filters.dataSource = ''
  filters.keyword = ''
  page.value = 1
  void loadCalls()
}

function handlePageChange(next: number) {
  page.value = next
  void loadCalls()
}

function handleSizeChange(next: number) {
  size.value = next
  page.value = 1
  void loadCalls()
}

function renderCharts() {
  if (!overview.value) return
  renderTrendChart()
  renderTypeChart()
  renderModelChart()
}

function renderTrendChart() {
  if (!trendChartRef.value || !overview.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const points = overview.value.trend
  trendChart.setOption({
    color: ['#6366f1', '#22d3ee'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderWidth: 0,
      textStyle: { color: '#e2e8f0', fontSize: 11 },
    },
    legend: {
      top: 0,
      right: 4,
      icon: 'circle',
      itemWidth: 6,
      itemHeight: 6,
      textStyle: { color: '#94a3b8', fontSize: 9 },
    },
    grid: { left: 12, right: 14, top: 30, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((point) => point.date),
      axisLine: { lineStyle: { color: '#e8edf5' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 9 },
    },
    yAxis: [
      {
        type: 'value',
        name: '调用',
        nameTextStyle: { color: '#a4afbf', fontSize: 8 },
        splitLine: { lineStyle: { color: '#f1f5f9' } },
        axisLabel: { color: '#a4afbf', fontSize: 8 },
      },
      {
        type: 'value',
        name: 'Token',
        nameTextStyle: { color: '#a4afbf', fontSize: 8 },
        splitLine: { show: false },
        axisLabel: { color: '#a4afbf', fontSize: 8 },
      },
    ],
    series: [
      {
        name: '调用量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(99,102,241,0.22)' },
              { offset: 1, color: 'rgba(99,102,241,0.01)' },
            ],
          },
        },
        data: points.map((point) => point.calls),
      },
      {
        name: 'Token',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 8,
        itemStyle: { borderRadius: [3, 3, 0, 0], opacity: 0.65 },
        data: points.map((point) => point.totalTokens),
      },
    ],
  }, true)
}

function renderTypeChart() {
  if (!typeChartRef.value || !overview.value) return
  if (!typeChart) typeChart = echarts.init(typeChartRef.value)
  const types = overview.value.types
  const totalCalls = overview.value.summary.totalCalls
  typeChart.setOption({
    color: ['#6366f1', '#06b6d4', '#a855f7', '#f59e0b', '#10b981', '#f43f5e', '#64748b'],
    tooltip: {
      trigger: 'item',
      backgroundColor: '#0f172a',
      borderWidth: 0,
      textStyle: { color: '#e2e8f0', fontSize: 11 },
      formatter: '{b}<br/>调用：{c}（{d}%）',
    },
    title: {
      text: String(totalCalls),
      subtext: '总调用',
      left: 'center',
      top: '34%',
      textStyle: { color: '#1e293b', fontSize: 22, fontWeight: 650 },
      subtextStyle: { color: '#a4afbf', fontSize: 9 },
    },
    legend: {
      bottom: 0,
      left: 'center',
      icon: 'circle',
      itemWidth: 6,
      itemHeight: 6,
      textStyle: { color: '#94a3b8', fontSize: 8 },
    },
    series: [
      {
        name: '调用类型',
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '40%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        data: types.map((type) => ({
          name: typeLabel(type.name),
          value: type.calls,
        })),
      },
    ],
  }, true)
}

function renderModelChart() {
  if (!modelChartRef.value || !overview.value) return
  if (!modelChart) modelChart = echarts.init(modelChartRef.value)
  const models = [...overview.value.models].reverse()
  modelChart.setOption({
    color: ['#6366f1'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#0f172a',
      borderWidth: 0,
      textStyle: { color: '#e2e8f0', fontSize: 11 },
      formatter: (params: unknown) => {
        const item = Array.isArray(params) ? params[0] as { name: string; value: number } : null
        return item ? `${item.name}<br/>调用：${item.value}` : ''
      },
    },
    grid: { left: 10, right: 24, top: 10, bottom: 6, containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#a4afbf', fontSize: 8 },
    },
    yAxis: {
      type: 'category',
      data: models.map((model) => model.name.length > 18 ? `${model.name.slice(0, 18)}…` : model.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 9 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#818cf8' },
              { offset: 1, color: '#22d3ee' },
            ],
          },
        },
        data: models.map((model) => model.calls),
      },
    ],
  }, true)
}

function handleResize() {
  trendChart?.resize()
  typeChart?.resize()
  modelChart?.resize()
}

watch(days, async () => {
  page.value = 1
  await Promise.all([loadOverview(), loadCalls()])
})

onMounted(async () => {
  await refreshAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  typeChart?.dispose()
  modelChart?.dispose()
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="OBSERVABILITY"
      title="调用监控"
      description="真实调用、Token、耗时、费用与 Agent/Tool/RAG/多模态执行域的集中观测"
    >
      <template #actions>
        <el-select v-model="days" style="width: 112px" size="default">
          <el-option label="最近 7 天" :value="7" />
          <el-option label="最近 14 天" :value="14" />
          <el-option label="最近 30 天" :value="30" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading || callsLoading" @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <div v-if="hasSimulated" class="simulated-banner">
      <el-icon><WarningFilled /></el-icon>
      <div>
        <strong>当前监控包含 SIMULATED 数据</strong>
        <span>
          共 {{ summary?.simulatedCalls ?? 0 }} 条标记为 SIMULATED 的模型调用，用于展示图表与表格布局；
          它们不代表真实供应商调用，也未计算费用。真实调用 {{ summary?.realCalls ?? 0 }} 条。
        </span>
      </div>
      <el-tag size="small" type="warning" effect="dark">SIMULATED</el-tag>
    </div>

    <section v-if="summary" class="kpi-grid">
      <article class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-icon is-indigo"><el-icon><DataLine /></el-icon></span>
          <span class="kpi-label">模型调用</span>
        </div>
        <strong class="kpi-value">{{ formatNumber(summary.totalCalls) }}</strong>
        <div class="kpi-foot">
          <span class="kpi-real">{{ formatNumber(summary.realCalls) }} real</span>
          <span class="kpi-sim">{{ formatNumber(summary.simulatedCalls) }} simulated</span>
        </div>
      </article>

      <article class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-icon is-emerald"><el-icon><TrendCharts /></el-icon></span>
          <span class="kpi-label">成功率</span>
        </div>
        <strong class="kpi-value">{{ summary.successRate }}<small>%</small></strong>
        <div class="kpi-foot">
          <span class="is-success">{{ formatNumber(summary.successCalls) }} 成功</span>
          <span class="is-danger">{{ formatNumber(summary.failedCalls) }} 失败</span>
        </div>
      </article>

      <article class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-icon is-cyan"><el-icon><Timer /></el-icon></span>
          <span class="kpi-label">平均耗时</span>
        </div>
        <strong class="kpi-value">{{ formatLatency(summary.avgLatencyMs) }}</strong>
        <div class="kpi-foot">
          <span>最近 {{ days }} 天请求</span>
        </div>
      </article>

      <article class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-icon is-amber"><el-icon><Cpu /></el-icon></span>
          <span class="kpi-label">Token 总量</span>
        </div>
        <strong class="kpi-value">{{ formatNumber(summary.totalTokens) }}</strong>
        <div class="kpi-foot">
          <span>输入 + 输出</span>
        </div>
      </article>

      <article class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-icon is-rose"><el-icon><Coin /></el-icon></span>
          <span class="kpi-label">已知费用</span>
        </div>
        <strong class="kpi-value" :class="{ 'is-unknown': summary.knownCost === undefined }">
          {{ formatCost(summary.knownCost) }}
        </strong>
        <div class="kpi-foot">
          <span class="is-warning">{{ formatNumber(summary.unknownCostCalls) }} 条费用未知</span>
        </div>
      </article>

      <article class="kpi-card">
        <div class="kpi-head">
          <span class="kpi-icon is-violet"><el-icon><MagicStick /></el-icon></span>
          <span class="kpi-label">执行域</span>
        </div>
        <strong class="kpi-value">{{ summary.agentExecutions + summary.toolCalls + summary.ragQueries + summary.multimodalTasks }}</strong>
        <div class="kpi-foot">
          <span>{{ summary.agentExecutions }} Agent</span>
          <span>{{ summary.toolCalls }} Tool</span>
          <span>{{ summary.ragQueries }} RAG</span>
        </div>
      </article>
    </section>

    <div class="chart-grid">
      <section class="panel chart-panel chart-panel--wide">
        <div class="panel-head">
          <div>
            <span class="panel-eyebrow">CALL VOLUME</span>
            <h3>调用趋势</h3>
          </div>
          <span>调用量 / Token</span>
        </div>
        <div ref="trendChartRef" class="chart chart--trend"></div>
      </section>

      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <span class="panel-eyebrow">CALL MIX</span>
            <h3>调用类型</h3>
          </div>
          <span>按请求类型</span>
        </div>
        <div ref="typeChartRef" class="chart chart--pie"></div>
      </section>
    </div>

    <div class="lower-grid">
      <section class="panel chart-panel">
        <div class="panel-head">
          <div>
            <span class="panel-eyebrow">MODEL RANKING</span>
            <h3>模型调用分布</h3>
          </div>
          <span>Top 6</span>
        </div>
        <div ref="modelChartRef" class="chart chart--models"></div>
      </section>

      <section class="panel ops-panel">
        <div class="panel-head">
          <div>
            <span class="panel-eyebrow">OPERATIONS</span>
            <h3>平台资产与执行</h3>
          </div>
          <span>当前配置</span>
        </div>
        <div class="ops-grid">
          <div>
            <span class="ops-icon is-cyan"><el-icon><Cpu /></el-icon></span>
            <strong>{{ summary?.activeModels ?? 0 }}</strong>
            <span>启用的模型</span>
          </div>
          <div>
            <span class="ops-icon is-violet"><el-icon><MagicStick /></el-icon></span>
            <strong>{{ summary?.enabledAgents ?? 0 }}</strong>
            <span>启用的 Agent</span>
          </div>
          <div>
            <span class="ops-icon is-emerald"><el-icon><SetUp /></el-icon></span>
            <strong>{{ summary?.enabledTools ?? 0 }}</strong>
            <span>启用的 Tool</span>
          </div>
          <div>
            <span class="ops-icon is-indigo"><el-icon><Collection /></el-icon></span>
            <strong>{{ summary?.knowledgeBases ?? 0 }}</strong>
            <span>知识库</span>
          </div>
          <div>
            <span class="ops-icon is-amber"><el-icon><Document /></el-icon></span>
            <strong>{{ summary?.knowledgeChunks ?? 0 }}</strong>
            <span>向量切片</span>
          </div>
          <div>
            <span class="ops-icon is-rose"><el-icon><Picture /></el-icon></span>
            <strong>{{ summary?.multimodalTasks ?? 0 }}</strong>
            <span>多模态任务</span>
          </div>
        </div>
        <div class="ops-foot">
          <span><el-icon><ChatDotRound /></el-icon>{{ summary?.agentExecutions ?? 0 }} 次 Agent 执行</span>
          <span><el-icon><SetUp /></el-icon>{{ summary?.toolCalls ?? 0 }} 次工具执行</span>
          <span><el-icon><Collection /></el-icon>{{ summary?.ragQueries ?? 0 }} 次 RAG 查询</span>
        </div>
      </section>
    </div>

    <section class="panel calls-panel">
      <div class="calls-head">
        <div>
          <span class="panel-eyebrow">CALL LOGS</span>
          <h3>模型调用日志</h3>
          <span class="calls-subtitle">
            共 {{ formatNumber(total) }} 条 · 费用未知时显示“未知”，不使用猜测值
            <template v-if="latestCall"> · 最近一次 {{ dayjs(latestCall.createdAt).format('MM-DD HH:mm:ss') }}</template>
          </span>
        </div>
        <div class="calls-filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索模型 / Trace / 摘要"
            :prefix-icon="Search"
            clearable
            style="width: 205px"
            @keyup.enter="handleSearch"
          />
          <el-select v-model="filters.requestType" placeholder="全部类型" clearable style="width: 120px">
            <el-option
              v-for="type in requestTypes"
              :key="type"
              :label="typeLabel(type)"
              :value="type"
            />
          </el-select>
          <el-select v-model="filters.dataSource" placeholder="数据源" clearable style="width: 108px">
            <el-option label="REAL" value="REAL" />
            <el-option label="SIMULATED" value="SIMULATED" />
          </el-select>
          <el-select v-model="filters.success" placeholder="结果" clearable style="width: 96px">
            <el-option label="成功" value="true" />
            <el-option label="失败" value="false" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <el-table v-loading="callsLoading" :data="calls" style="width: 100%">
        <el-table-column label="时间" width="138">
          <template #default="{ row }: { row: MonitorCallLog }">
            <span class="call-time">{{ dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="112">
          <template #default="{ row }: { row: MonitorCallLog }">
            <el-tag size="small" effect="plain" type="info">{{ typeLabel(row.requestType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型" min-width="200">
          <template #default="{ row }: { row: MonitorCallLog }">
            <div class="call-model">
              <strong>{{ row.modelName || '未知模型' }}</strong>
              <span>{{ row.providerCode || '—' }}<template v-if="row.traceId"> · {{ row.traceId }}</template></span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Token" width="116">
          <template #default="{ row }: { row: MonitorCallLog }">
            <div class="call-token">
              <strong>{{ formatToken(row.totalTokens) }}</strong>
              <span>{{ row.promptTokens ?? '—' }} / {{ row.completionTokens ?? '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="86">
          <template #default="{ row }: { row: MonitorCallLog }">
            <span class="call-mono">{{ formatLatency(row.latencyMs) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="费用" width="100">
          <template #default="{ row }: { row: MonitorCallLog }">
            <span :class="['call-cost', { 'is-unknown': !row.costKnown }]">
              {{ row.costKnown ? formatCost(row.estimatedCost) : '未知' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="82">
          <template #default="{ row }: { row: MonitorCallLog }">
            <el-tag :type="row.success ? 'success' : 'danger'" effect="light" size="small">
              {{ statusLabel(row.success) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据源" width="102">
          <template #default="{ row }: { row: MonitorCallLog }">
            <el-tag
              :type="row.dataSource === 'SIMULATED' ? 'warning' : 'success'"
              effect="plain"
              size="small"
            >
              {{ sourceLabel(row.dataSource) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="220">
          <template #default="{ row }: { row: MonitorCallLog }">
            <span class="call-summary">{{ row.requestSummary || row.errorMessage || '—' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="calls-empty">
            <span class="calls-empty__icon"><el-icon :size="22"><DataLine /></el-icon></span>
            <strong>没有符合条件的调用</strong>
            <p>调整筛选条件，或先在模型/Agent/RAG/多模态页面发起一次真实调用。</p>
          </div>
        </template>
      </el-table>

      <div class="calls-footer">
        <span class="calls-footer__note">
          <el-icon><WarningFilled /></el-icon>
          费用仅在模型配置输入/输出单价且供应商返回 Token 时计算；SIMULATED 行不计入真实财务结论。
        </span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.simulated-banner {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 14px;
  margin-bottom: 14px;
  border: 1px solid rgba(245, 158, 11, 0.15);
  border-radius: 9px;
  color: #92400e;
  background: #fffbeb;

  > .el-icon {
    margin-top: 1px;
    color: #f59e0b;
    flex: 0 0 auto;
  }

  > div {
    display: flex;
    flex-direction: column;
    flex: 1;

    strong {
      font-size: 9px;
      font-weight: 650;
    }

    span {
      margin-top: 2px;
      color: #a16207;
      font-size: 8px;
      line-height: 1.55;
    }
  }
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.kpi-card {
  padding: 13px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 11px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.kpi-head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.kpi-icon {
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border-radius: 6px;
  font-size: 12px;

  &.is-indigo {
    color: #4f46e5;
    background: #eef2ff;
  }

  &.is-emerald {
    color: #059669;
    background: #ecfdf5;
  }

  &.is-cyan {
    color: #0891b2;
    background: #ecfeff;
  }

  &.is-amber {
    color: #b45309;
    background: #fffbeb;
  }

  &.is-rose {
    color: #be123c;
    background: #fff1f2;
  }

  &.is-violet {
    color: #7e22ce;
    background: #faf5ff;
  }
}

.kpi-label {
  color: #94a3b8;
  font-size: 8.5px;
}

.kpi-value {
  display: block;
  margin-top: 9px;
  color: #0f172a;
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.03em;

  small {
    margin-left: 2px;
    color: #94a3b8;
    font-size: 10px;
  }

  &.is-unknown {
    color: #b45309;
    font-size: 17px;
  }
}

.kpi-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 6px;
  color: #a4afbf;
  font-size: 7px;

  span {
    white-space: nowrap;
  }

  .kpi-real,
  .is-success {
    color: #059669;
  }

  .kpi-sim {
    color: #b45309;
  }

  .is-danger {
    color: #dc2626;
  }

  .is-warning {
    color: #b45309;
  }
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.lower-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.panel {
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.chart-panel {
  padding: 14px 16px 10px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 4px;

  > div {
    display: flex;
    flex-direction: column;
  }

  h3 {
    margin: 3px 0 0;
    color: #334155;
    font-size: 11px;
    font-weight: 650;
  }

  > span {
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7px;
  }
}

.panel-eyebrow {
  color: #b0bac9;
  font-family: var(--font-mono);
  font-size: 6.5px;
  letter-spacing: 0.12em;
}

.chart {
  width: 100%;

  &--trend {
    height: 235px;
  }

  &--pie {
    height: 235px;
  }

  &--models {
    height: 230px;
  }
}

.ops-panel {
  padding: 14px 16px;
}

.ops-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;

  > div {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 9px;
    border-radius: 7px;
    background: #fbfcfe;

    strong {
      margin-top: 5px;
      color: #334155;
      font-family: var(--font-mono);
      font-size: 16px;
      font-weight: 650;
      line-height: 1;
    }

    > span:last-child {
      margin-top: 3px;
      color: #a4afbf;
      font-size: 7px;
    }
  }
}

.ops-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 5px;
  font-size: 10px;

  &.is-cyan {
    color: #0891b2;
    background: #ecfeff;
  }

  &.is-violet {
    color: #7e22ce;
    background: #faf5ff;
  }

  &.is-emerald {
    color: #059669;
    background: #ecfdf5;
  }

  &.is-indigo {
    color: #4f46e5;
    background: #eef2ff;
  }

  &.is-amber {
    color: #b45309;
    background: #fffbeb;
  }

  &.is-rose {
    color: #be123c;
    background: #fff1f2;
  }
}

.ops-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 9px;
  margin-top: 9px;
  border-top: 1px solid #f1f5f9;
  color: #94a3b8;
  font-size: 7px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 3px;
  }

  .el-icon {
    color: #a5b4fc;
  }
}

.calls-panel {
  padding: 14px 16px 12px;
}

.calls-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 10px;

  > div:first-child {
    display: flex;
    flex-direction: column;
  }

  h3 {
    margin: 3px 0 0;
    color: #334155;
    font-size: 12px;
    font-weight: 650;
  }
}

.calls-subtitle {
  margin-top: 3px;
  color: #a4afbf;
  font-size: 7.5px;
}

.calls-filters {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.call-time {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.call-model {
  display: flex;
  flex-direction: column;
  min-width: 0;

  strong {
    overflow: hidden;
    color: #475569;
    font-size: 9px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  span {
    margin-top: 2px;
    overflow: hidden;
    color: #b0bac9;
    font-family: var(--font-mono);
    font-size: 6.5px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.call-token {
  display: flex;
  flex-direction: column;

  strong {
    color: #475569;
    font-family: var(--font-mono);
    font-size: 8.5px;
    font-weight: 600;
  }

  span {
    margin-top: 2px;
    color: #b0bac9;
    font-family: var(--font-mono);
    font-size: 6.5px;
  }
}

.call-mono {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 8px;
}

.call-cost {
  color: #475569;
  font-family: var(--font-mono);
  font-size: 8px;

  &.is-unknown {
    color: #b45309;
    font-size: 7.5px;
  }
}

.call-summary {
  display: -webkit-box;
  overflow: hidden;
  color: #7c8ba3;
  font-size: 8px;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.calls-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 35px 20px;

  &__icon {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border: 1px dashed #cbd5e1;
    border-radius: 12px;
    color: #94a3b8;
    background: #f8fafc;
  }

  strong {
    margin-top: 10px;
    color: #475569;
    font-size: 10px;
  }

  p {
    margin: 4px 0 0;
    color: #a4afbf;
    font-size: 8px;
  }
}

.calls-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-top: 12px;

  &__note {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: #a4afbf;
    font-size: 7px;

    .el-icon {
      color: #f59e0b;
    }
  }
}

@media (max-width: 1320px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .lower-grid {
    grid-template-columns: 1fr;
  }

  .calls-head {
    flex-direction: column;
  }

  .calls-filters {
    justify-content: flex-start;
  }
}
</style>
