<template>
  <div>
    <div class="row">
      <el-button type="primary" @click="openCreate">新增接口</el-button>
    </div>
    <el-table :data="apis" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="接口名称" min-width="140" />
      <el-table-column prop="method" label="方法" width="90">
        <template #default="{ row }">
          <el-tag :type="methodTag(row.method)" effect="dark">{{ row.method }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="url" label="URL" min-width="280" show-overflow-tooltip />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="success" :loading="tryingId === row.id" @click="tryApi(row)">▶ 试一下</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该接口？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑接口' : '新增接口'" width="640px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="接口名称">
          <el-input v-model="form.name" placeholder="如：用户登录" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="form.method" style="width: 140px">
            <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="form.url" placeholder="http://localhost:8081/api/v1/..." />
        </el-form-item>
        <el-form-item label="Headers">
          <el-input v-model="form.headers" type="textarea" :rows="2"
                    placeholder='JSON 对象，如 {"Content-Type":"application/json"}' />
        </el-form-item>
        <el-form-item label="查询参数">
          <el-input v-model="form.params" type="textarea" :rows="2"
                    placeholder='JSON 对象（GET 的 query string），如 {"page":"1"}' />
        </el-form-item>
        <el-form-item label="请求体">
          <el-input v-model="form.body" type="textarea" :rows="4" placeholder='POST/PUT 的 body，JSON 文本' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tryVisible" title="试调结果" width="720px">
      <template v-if="tryResult">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态码">
            <el-tag :type="tryResult.status < 400 ? 'success' : 'danger'">{{ tryResult.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ tryResult.timeMs }} ms</el-descriptions-item>
        </el-descriptions>
        <h4>响应体</h4>
        <pre class="resp">{{ tryResult.body }}</pre>
        <h4>响应头</h4>
        <pre class="resp small">{{ tryResult.headers }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const props = defineProps({ projectId: String })

const apis = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)
const tryingId = ref(null)
const tryVisible = ref(false)
const tryResult = ref(null)

const form = reactive({ name: '', method: 'GET', url: '', headers: '', params: '', body: '' })

async function load() {
  loading.value = true
  try {
    apis.value = await http.get(`/api/projects/${props.projectId}/apis`)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', method: 'GET', url: '', headers: '', params: '', body: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    name: row.name, method: row.method, url: row.url,
    headers: row.headers || '', params: row.params || '', body: row.body || ''
  })
  dialogVisible.value = true
}

async function save() {
  const payload = { ...form, headers: form.headers || null, params: form.params || null, body: form.body || null }
  if (editing.value) {
    await http.put(`/api/apis/${editing.value.id}`, payload)
  } else {
    await http.post(`/api/projects/${props.projectId}/apis`, payload)
  }
  ElMessage.success('已保存')
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await http.delete(`/api/apis/${row.id}`)
  ElMessage.success('已删除')
  load()
}

async function tryApi(row) {
  tryingId.value = row.id
  try {
    tryResult.value = await http.post(`/api/apis/${row.id}/try`)
    tryVisible.value = true
  } finally {
    tryingId.value = null
  }
}

function methodTag(m) {
  return { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', PATCH: 'info' }[m] || 'info'
}

onMounted(load)
</script>

<style scoped>
.row { display: flex; justify-content: flex-end; margin-bottom: 10px; }
.resp { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; max-height: 260px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
.resp.small { max-height: 140px; font-size: 12px; }
h4 { margin: 14px 0 6px; }
</style>
