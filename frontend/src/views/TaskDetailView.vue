<template>
  <el-card>
    <div class="toolbar">
      <div>
        <el-button link @click="$router.push('/tasks')">← 任务列表</el-button>
        <h3 class="inline">任务 #{{ taskId }} 报告</h3>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-descriptions v-if="task" :column="4" border class="summary">
      <el-descriptions-item label="状态">
        <el-tag :type="task.status === 'SUCCESS' ? 'success' : 'danger'" effect="dark">{{ task.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="触发">{{ task.triggerType }}</el-descriptions-item>
      <el-descriptions-item label="通过 / 总数">
        <span class="pass">{{ task.passCount }}</span> / {{ task.totalCount }}
      </el-descriptions-item>
      <el-descriptions-item label="总耗时">{{ task.durationMs }}ms</el-descriptions-item>
    </el-descriptions>

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
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import http from '../api'

const route = useRoute()
const taskId = computed(() => route.params.id)

const task = ref(null)
const results = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    ;[task.value, results.value] = await Promise.all([
      http.get(`/api/tasks/${taskId.value}`),
      http.get(`/api/tasks/${taskId.value}/results`)
    ])
  } finally {
    loading.value = false
  }
}

function pretty(text) {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.inline { display: inline; margin-left: 8px; }
.summary { margin-bottom: 16px; }
.detail { margin-bottom: 16px; }
.pass { color: #67c23a; font-weight: 700; }
.fail { color: #f56c6c; }
.resp { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; max-height: 240px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-size: 12px; }
h4 { margin: 10px 0 6px; }
</style>
