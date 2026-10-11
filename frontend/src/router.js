import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/LoginView.vue') },
  { path: '/', component: () => import('./views/ProjectsView.vue') },
  { path: '/projects/:id', component: () => import('./views/ProjectView.vue') },
  { path: '/tasks', component: () => import('./views/TasksView.vue') },
  { path: '/tasks/:id', component: () => import('./views/TaskDetailView.vue') }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(to => {
  if (to.path !== '/login' && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router
