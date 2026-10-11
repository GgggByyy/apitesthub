<template>
  <div>
    <div class="row">
      <el-button type="primary" @click="openCreate">新增套件</el-button>
    </div>
    <el-table :data="suites" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="套件名称" min-width="180" />
      <el-table-column label="用例数" width="90">
        <template #default="{ row }">{{ parseIds(row).length }}</template>
      </el-table-column>
      <el-table-column label="用例（按执行顺序）" min-width="300">
        <template #default="{ row }">
          <el-tag v-for="id in parseIds(row)" :key="id" size="small" class="tag">
            {{ caseName(id) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="success" :loading="executingId === row.id" @click="execute(row)">▶ 执行</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该套件？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑套件' : '新增套件'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="套件名称">
          <el-input v-model="form.name" placeholder="如：家庭健康管家回归套件" />
        </el-form-item>
        <el-form-item label="选择用例">
          <el-select v-model="form.caseIds" multiple placeholder="按点选顺序执行" style="width: 100%">
            <el-option v-for="c in cases" :key="c.id" :label="`#${c.id} ${c.name}`" :value="c.id" />
          </el-select>
          <div class="hint">用例按选择顺序执行（M3 起支持链式变量传递，顺序即依赖顺序）</div>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'

const props = defineProps({ projectId: String })
const router = useRouter()

const suites = ref([])
const cases = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)
const executingId = ref(null)

const form = reactive({ name: '', caseIds: [] })

async function load() {
  loading.value = true
  try {
    ;[suites.value, cases.value] = await Promise.all([
      http.get(`/api/projects/${props.projectId}/suites`),
      http.get(`/api/projects/${props.projectId}/cases`)
    ])
  } finally {
    loading.value = false
  }
}

function parseIds(row) {
  try {
    return JSON.parse(row.caseIds || '[]')
  } catch {
    return []
  }
}

function caseName(id) {
  const c = cases.value.find(x => x.id === id)
  return c ? c.name : id
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', caseIds: [] })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, { name: row.name, caseIds: parseIds(row) })
  dialogVisible.value = true
}

async function save() {
  if (editing.value) {
    await http.put(`/api/suites/${editing.value.id}`, form)
  } else {
    await http.post(`/api/projects/${props.projectId}/suites`, form)
  }
  ElMessage.success('已保存')
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await http.delete(`/api/suites/${row.id}`)
  ElMessage.success('已删除')
  load()
}

async function execute(row) {
  executingId.value = row.id
  try {
    const task = await http.post(`/api/suites/${row.id}/execute`)
    ElMessage.success(`执行完成：${task.passCount} 通过 / ${task.failCount} 失败`)
    router.push(`/tasks/${task.id}`)
  } finally {
    executingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.row { display: flex; justify-content: flex-end; margin-bottom: 10px; }
.tag { margin-right: 4px; margin-bottom: 2px; }
.hint { color: #999; font-size: 12px; line-height: 1.6; }
</style>
