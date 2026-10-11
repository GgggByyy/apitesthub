<template>
  <el-container class="layout">
    <el-header class="header">
      <span class="brand" @click="$router.push('/')">🧪 ApitestHub</span>
      <span class="sub">接口自动化测试平台</span>
      <el-button v-if="$route.path !== '/login'" link type="danger" @click="logout">退出</el-button>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style>
body { margin: 0; background: #f5f7fa; }
.layout { min-height: 100vh; }
.header {
  display: flex; align-items: center; gap: 12px;
  background: #001529; color: #fff;
}
.brand { font-size: 20px; font-weight: 700; cursor: pointer; }
.sub { color: #999; flex: 1; }
</style>
