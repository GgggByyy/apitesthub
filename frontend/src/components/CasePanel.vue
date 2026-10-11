<template>
  <div>
    <div class="row">
      <el-button type="primary" @click="openCreate">新增用例</el-button>
    </div>
    <el-table :data="cases" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="sortOrder" label="顺序" width="70" />
      <el-table-column prop="name" label="用例名称" min-width="160" />
      <el-table-column label="目标接口" min-width="140">
        <template #default="{ row }">{{ apiName(row.apiId) }}</template>
      </el-table-column>
      <el-table-column label="断言" min-width="240">
        <template #default="{ row }">
          <el-tag v-for="(a, i) in parseAssertions(row)" :key="i" size="small" class="tag">
            {{ shortAssertion(a) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该用例？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用例' : '新增用例'" width="700px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用例名称">
          <el-input v-model="form.name" placeholder="如：登录-状态码+业务码" />
        </el-form-item>
        <el-form-item label="目标接口">
          <el-select v-model="form.apiId" placeholder="选择接口" style="width: 100%">
            <el-option v-for="a in apis" :key="a.id" :label="`#${a.id} ${a.name} (${a.method})`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行顺序">
          <el-input-number v-model="form.sortOrder" :min="0" />
          <span class="hint">套件内按此值升序执行</span>
        </el-form-item>
        <el-form-item label="断言集合">
          <div v-for="(a, i) in form.assertions" :key="i" class="assert-row">
            <el-select v-model="a.type" style="width: 150px" placeholder="类型">
              <el-option label="状态码" value="status" />
              <el-option label="响应体JSONPath" value="body_jsonpath" />
              <el-option label="响应时间ms" value="response_time_ms" />
            </el-select>
            <el-input v-if="a.type === 'body_jsonpath'" v-model="a.path" placeholder="$.code"
                      style="width: 130px" />
            <el-select v-model="a.op" style="width: 90px" placeholder="操作">
              <el-option v-for="o in ['eq','ne','gt','lt','contains']" :key="o" :label="o" :value="o" />
            </el-select>
            <el-input v-model="a.expect" placeholder="期望值" style="width: 140px" />
            <el-button link type="danger" @click="form.assertions.splice(i, 1)">✕</el-button>
          </div>
          <el-button size="small" @click="form.assertions.push({ type: 'status', op: 'eq', expect: '200', path: '' })">
            + 添加断言
          </el-button>
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

const cases = ref([])
const apis = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)

const form = reactive({ name: '', apiId: null, sortOrder: 0, assertions: [] })

async function load() {
  loading.value = true
  try {
    ;[cases.value, apis.value] = await Promise.all([
      http.get(`/api/projects/${props.projectId}/cases`),
      http.get(`/api/projects/${props.projectId}/apis`)
    ])
  } finally {
    loading.value = false
  }
}

function apiName(apiId) {
  const api = apis.value.find(a => a.id === apiId)
  return api ? `#${api.id} ${api.name}` : apiId
}

function parseAssertions(row) {
  try {
    return JSON.parse(row.assertions || '[]')
  } catch {
    return []
  }
}

function shortAssertion(a) {
  if (a.type === 'body_jsonpath') return `${a.path} ${a.op} ${a.expect}`
  return `${a.type} ${a.op} ${a.expect}`
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', apiId: null, sortOrder: 0, assertions: [{ type: 'status', op: 'eq', expect: '200', path: '' }] })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    apiId: row.apiId,
    sortOrder: row.sortOrder,
    assertions: parseAssertions(row).map(a => ({ ...a, expect: String(a.expect), path: a.path || '' }))
  })
  dialogVisible.value = true
}

async function save() {
  // expect 统一转字符串交给后端（数字型字符串会被 Jackson 正确反序列化）
  const payload = {
    name: form.name,
    apiId: form.apiId,
    sortOrder: form.sortOrder,
    assertions: form.assertions.length ? JSON.stringify(form.assertions) : null
  }
  if (editing.value) {
    await http.put(`/api/cases/${editing.value.id}`, payload)
  } else {
    await http.post(`/api/projects/${props.projectId}/cases`, payload)
  }
  ElMessage.success('已保存')
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await http.delete(`/api/cases/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.row { display: flex; justify-content: flex-end; margin-bottom: 10px; }
.tag { margin-right: 4px; }
.assert-row { display: flex; gap: 6px; margin-bottom: 8px; align-items: center; }
.hint { margin-left: 10px; color: #999; font-size: 12px; }
</style>
