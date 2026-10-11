<template>
  <el-card>
    <div class="toolbar">
      <h3>执行任务</h3>
      <el-button link @click="$router.push('/')">← 返回项目</el-button>
    </div>
    <el-table :data="tasks" v-loading="loading" @row-click="enter" row-class-name="clickable">
      <el-table-column prop="id" label="任务ID" width="80" />
      <el-table-column prop="suiteId" label="套件" width="80" />
      <el-table-column prop="triggerType" label="触发方式" width="110" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" effect="dark">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通过率" min-width="160">
        <template #default="{ row }">
          {{ row.passCount }}/{{ row.totalCount }}
          <el-tag v-if="row.totalCount" size="small" :type="row.failCount ? 'danger' : 'success'" class="pct">
            {{ Math.round(row.passCount / row.totalCount * 100) }}%
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="总耗时" width="100">
        <template #default="{ row }">{{ row.durationMs }}ms</template>
      </el-table-column>
      <el-table-column prop="startedAt" label="开始时间" min-width="170" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api'

const router = useRouter()
const tasks = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    tasks.value = await http.get('/api/tasks')
  } finally {
    loading.value = false
  }
}

function enter(row) {
  router.push(`/tasks/${row.id}`)
}

function statusTag(s) {
  return { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info' }[s] || 'info'
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
:deep(.clickable) { cursor: pointer; }
.pct { margin-left: 6px; }
</style>
