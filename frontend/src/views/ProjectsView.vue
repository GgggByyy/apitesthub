<template>
  <el-card>
    <div class="toolbar">
      <h3>我的项目</h3>
      <el-button type="primary" @click="openCreate">新建项目</el-button>
    </div>

    <el-table :data="projects" v-loading="loading" @row-click="enter" row-class-name="clickable">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="项目名称" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
          <el-popconfirm title="删除项目将同时删除其下全部接口定义，确认？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger" @click.stop>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑项目' : '新建项目'" width="480px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'

const router = useRouter()
const projects = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)
const form = reactive({ name: '', description: '' })

async function load() {
  loading.value = true
  try {
    projects.value = await http.get('/api/projects')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  form.name = row.name
  form.description = row.description
  dialogVisible.value = true
}

async function save() {
  if (editing.value) {
    await http.put(`/api/projects/${editing.value.id}`, form)
  } else {
    await http.post('/api/projects', form)
  }
  ElMessage.success('已保存')
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await http.delete(`/api/projects/${row.id}`)
  ElMessage.success('已删除')
  load()
}

function enter(row) {
  router.push(`/projects/${row.id}`)
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
:deep(.clickable) { cursor: pointer; }
</style>
