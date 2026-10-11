<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>ApitestHub 登录</h2>
      <el-form :model="form" label-width="70px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="tester" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">登录</el-button>
          <el-button @click="registerVisible = true">注册</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="registerVisible" title="注册新用户" width="420px">
      <el-form :model="regForm" label-width="70px">
        <el-form-item label="用户名">
          <el-input v-model="regForm.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="regForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" @click="register">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '' })
const loading = ref(false)
const registerVisible = ref(false)

async function submit() {
  loading.value = true
  try {
    const data = await http.post('/api/user/login', form)
    localStorage.setItem('token', data.token)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function register() {
  await http.post('/api/user/register', regForm)
  ElMessage.success('注册成功，请登录')
  form.username = regForm.username
  form.password = ''
  registerVisible.value = false
}
</script>

<style scoped>
.login-wrap { display: flex; justify-content: center; padding-top: 10vh; }
.login-card { width: 400px; }
</style>
