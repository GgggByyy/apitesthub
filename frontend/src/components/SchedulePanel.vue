<template>
  <div>
    <div class="row">
      <el-button type="primary" @click="openCreate">新增定时计划</el-button>
    </div>
    <el-table :data="jobs" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="套件" min-width="160">
        <template #default="{ row }">{{ suiteName(row.suiteId) }}</template>
      </el-table-column>
      <el-table-column prop="cronExpr" label="Cron 表达式" min-width="140">
        <template #default="{ row }"><code>{{ row.cronExpr }}</code></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="dark">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastFireTime" label="上次触发" min-width="170" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该计划？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑计划' : '新增计划'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="套件">
          <el-select v-model="form.suiteId" placeholder="选择套件" style="width: 100%">
            <el-option v-for="s in suites" :key="s.id" :label="`#${s.id} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <el-input v-model="form.cronExpr" placeholder="0 * * * * ?（每分钟）" />
          <div class="hint">Quartz 格式：秒 分 时 日 月 周 —— 0 0 8 * * ? 表示每天 8 点</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const props = defineProps({ projectId: String })

const jobs = ref([])
const suites = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)

const form = reactive({ suiteId: null, cronExpr: '0 * * * * ?', enabled: true })

async function load() {
  loading.value = true
  try {
    ;[jobs.value, suites.value] = await Promise.all([
      http.get(`/api/projects/${props.projectId}/schedules`),
      http.get(`/api/projects/${props.projectId}/suites`)
    ])
  } finally {
    loading.value = false
  }
}

function suiteName(id) {
  const s = suites.value.find(x => x.id === id)
  return s ? `#${s.id} ${s.name}` : id
}

function openCreate() {
  editing.value = null
  Object.assign(form, { suiteId: null, cronExpr: '0 * * * * ?', enabled: true })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    suiteId: row.suiteId,
    cronExpr: row.cronExpr,
    enabled: row.enabled === 1
  })
  dialogVisible.value = true
}

async function save() {
  const payload = { ...form, enabled: form.enabled ? 1 : 0 }
  if (editing.value) {
    await http.put(`/api/schedules/${editing.value.id}`, payload)
  } else {
    await http.post(`/api/projects/${props.projectId}/schedules`, payload)
  }
  ElMessage.success('已保存，调度即刻生效')
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await http.delete(`/api/schedules/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.row { display: flex; justify-content: flex-end; margin-bottom: 10px; }
.hint { color: #999; font-size: 12px; line-height: 1.6; }
</style>
