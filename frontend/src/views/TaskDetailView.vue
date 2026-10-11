<template>
  <el-card>
    <div class="toolbar">
      <div>
        <el-button link @click="$router.push('/tasks')">← 任务列表</el-button>
        <h3 class="inline">任务 #{{ taskId }} 报告</h3>
        <el-tag v-if="wsConnected" type="success" size="small" effect="dark" class="live">● LIVE</el-tag>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-descriptions v-if="task" :column="4" border class="summary">
      <el-descriptions-item label="状态">
        <el-tag :type="statusTag(task.status)" effect="dark">{{ task.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="触发">{{ task.triggerType }}</el-descriptions-item>
      <el-descriptions-item label="通过 / 总数">
        <span class="pass">{{ task.passCount }}</span> / {{ task.totalCount }}
      </el-descriptions-item>
      <el-descriptions-item label="总耗时">{{ task.durationMs != null ? task.durationMs + 'ms' : '—' }}</el-descriptions-item>
    </el-descriptions>

    <el-card v-if="report" shadow="never" class="report-box">
      <template #header>
        <div class="report-head">
          <span>📊 测试报告（通过率 {{ report.passRate }}%）</span>
          <div>
            <el-button size="small" @click="exportReport('json')">导出 JSON</el-button>
            <el-button size="small" type="primary" @click="exportReport('html')">导出 HTML</el-button>
          </div>
        </div>
      </template>
      <div class="charts">
        <div ref="pieChart" class="chart"></div>
        <div ref="barChart" class="chart wide"></div>
      </div>
      <el-alert v-if="report.slowCases.length" type="warning" :closable="false" class="slow-alert">
        🐢 慢接口 {{ report.slowCases.length }} 个（>{{ report.slowThresholdMs }}ms）：
        {{ report.slowCases.map(c => `${c.caseName}(${c.timeMs}ms)`).join('、') }}
      </el-alert>
    </el-card>

    <el-card v-if="liveLogs.length" shadow="never" class="live-box">
      <template #header>实时日志（WebSocket 推送，共 {{ liveLogs.length }} 条）</template>
      <div ref="logBox" class="logs">
        <div v-for="(l, i) in liveLogs" :key="i" class="log-line" :class="{ ok: l.passed, bad: l.passed === false }">
          <span class="idx">{{ i + 1 }}</span>
          <span class="mark">{{ l.passed === true ? '✓' : l.passed === false ? '✗' : '🏁' }}</span>
          <span class="name">{{ l.caseName }}</span>
          <span v-if="l.timeMs != null" class="ms">{{ l.timeMs }}ms</span>
          <span v-if="l.progress" class="pg">{{ l.progress }}</span>
        </div>
      </div>
    </el-card>

    <el-table :data="results" v-loading="loading" class="detail">
      <el-table-column type="expand">
        <template #default="{ row }">
          <h4>请求快照</h4>
          <pre class="resp">{{ pretty(row.requestSnapshot) }}</pre>
          <h4>响应体</h4>
          <pre class="resp">{{ pretty(row.responseBody) }}</pre>
        </template>
      </el-table-column>
      <el-table-column prop="caseName" label="用例" min-width="180" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.passed ? 'success' : 'danger'" effect="dark">{{ row.passed ? '通过' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="responseStatus" label="状态码" width="90" />
      <el-table-column prop="responseTimeMs" label="耗时" width="90">
        <template #default="{ row }">{{ row.responseTimeMs }}ms</template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="320" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ fail: !row.passed }">{{ row.failReason || '—' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import http, { download } from '../api'

const route = useRoute()
const taskId = computed(() => route.params.id)

const task = ref(null)
const results = ref([])
const loading = ref(false)
const liveLogs = ref([])
const logBox = ref(null)
const wsConnected = ref(false)
const report = ref(null)
const pieChart = ref(null)
const barChart = ref(null)
let ws = null

async function load() {
  loading.value = true
  try {
    ;[task.value, results.value] = await Promise.all([
      http.get(`/api/tasks/${taskId.value}`),
      http.get(`/api/tasks/${taskId.value}/results`)
    ])
    if (task.value.status !== 'RUNNING') {
      await loadReport()
    }
    maybeConnectWs()
  } finally {
    loading.value = false
  }
}

async function loadReport() {
  report.value = await http.get(`/api/tasks/${taskId.value}/report`)
  await nextTick()
  renderCharts()
}

function renderCharts() {
  if (!report.value) return
  const pie = echarts.init(pieChart.value)
  pie.setOption({
    title: { text: '通过 / 失败', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '68%'],
      label: { show: false },
      data: [
        { value: report.value.pie.pass, name: '通过', itemStyle: { color: '#67c23a' } },
        { value: report.value.pie.fail, name: '失败', itemStyle: { color: '#f56c6c' } }
      ]
    }]
  })
  const bar = echarts.init(barChart.value)
  const bars = report.value.bars
  bar.setOption({
    title: { text: '用例耗时 Top（ms）', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: {},
    grid: { left: 40, right: 16, bottom: 70 },
    xAxis: { type: 'category', data: bars.map(b => b.caseName), axisLabel: { rotate: 38, fontSize: 10 } },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: bars.map(b => ({ value: b.timeMs, itemStyle: { color: b.slow ? '#e6a23c' : '#409eff' } })),
      label: { show: true, position: 'top', fontSize: 10 }
    }]
  })
}

function exportReport(fmt) {
  download(`/api/tasks/${taskId.value}/report/export/${fmt}`, `apitesthub-report-${taskId.value}.${fmt}`)
}

/** 任务进行中建立 WS 订阅（token 走 query：浏览器 WS API 不能自定义 header） */
function maybeConnectWs() {
  if (task.value?.status === 'RUNNING' && !ws) {
    const token = localStorage.getItem('token')
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    ws = new WebSocket(`${proto}://localhost:8080/ws/task?taskId=${taskId.value}&token=${token}`)
    ws.onopen = () => { wsConnected.value = true }
    ws.onmessage = async ev => {
      const m = JSON.parse(ev.data)
      if (m.event === 'case') {
        liveLogs.value.push(m)
        nextTick(() => logBox.value?.scrollTo({ top: logBox.value.scrollHeight }))
      } else if (m.event === 'finish') {
        liveLogs.value.push({ caseName: `执行完成：${m.status} ${m.passCount}/${m.passCount + m.failCount}`, passed: null })
        closeWs()
        await load() // 终态后拉取完整报告（含图表）
      }
    }
    ws.onclose = () => { wsConnected.value = false }
    ws.onerror = () => { wsConnected.value = false }
  }
}

function closeWs() {
  if (ws) { ws.close(); ws = null; wsConnected.value = false }
}

function pretty(text) {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

function statusTag(s) {
  return { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info' }[s] || 'info'
}

onMounted(load)
onUnmounted(closeWs)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.inline { display: inline; margin-left: 8px; }
.live { margin-left: 10px; }
.summary { margin-bottom: 16px; }
.detail { margin-bottom: 16px; }
.pass { color: #67c23a; font-weight: 700; }
.fail { color: #f56c6c; }
.report-box { margin-bottom: 16px; }
.report-head { display: flex; justify-content: space-between; align-items: center; }
.charts { display: flex; gap: 16px; align-items: stretch; }
.chart { width: 280px; height: 300px; flex-shrink: 0; }
.chart.wide { width: 60%; min-width: 400px; }
.slow-alert { margin-top: 12px; }
.live-box { margin-bottom: 16px; }
.logs { background: #1e1e1e; border-radius: 6px; padding: 10px; max-height: 300px; overflow: auto; font-family: Consolas, monospace; font-size: 13px; }
.log-line { display: flex; gap: 10px; padding: 2px 6px; color: #d4d4d4; }
.log-line.ok .mark { color: #67c23a; }
.log-line.bad .mark { color: #f56c6c; }
.idx { color: #666; min-width: 32px; text-align: right; }
.name { flex: 1; }
.ms { color: #8cc; }
.pg { color: #999; }
.resp { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; max-height: 240px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-size: 12px; }
h4 { margin: 10px 0 6px; }
</style>
