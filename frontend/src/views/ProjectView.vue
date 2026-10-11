<template>
  <el-card>
    <div class="toolbar">
      <div>
        <el-button link @click="$router.push('/')">← 返回</el-button>
        <h3 class="inline">项目 #{{ projectId }}</h3>
      </div>
      <el-button type="primary" plain @click="$router.push('/tasks')">📋 执行任务</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="接口定义" name="apis">
        <ApiPanel :project-id="projectId" />
      </el-tab-pane>
      <el-tab-pane label="测试用例" name="cases">
        <CasePanel :project-id="projectId" />
      </el-tab-pane>
      <el-tab-pane label="测试套件" name="suites">
        <SuitePanel :project-id="projectId" />
      </el-tab-pane>
      <el-tab-pane label="定时计划" name="schedules">
        <SchedulePanel :project-id="projectId" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import ApiPanel from '../components/ApiPanel.vue'
import CasePanel from '../components/CasePanel.vue'
import SuitePanel from '../components/SuitePanel.vue'
import SchedulePanel from '../components/SchedulePanel.vue'

const route = useRoute()
const projectId = computed(() => route.params.id)
const tab = ref('apis')
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.inline { display: inline; margin-left: 8px; }
</style>
