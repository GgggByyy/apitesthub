import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/LoginView.vue') },
  { path: '/', component: () => import('./views/ProjectsView.vue') },
  { path: '/projects/:id', component: () => import('./views/ApisView.vue') }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(to => {
  if (to.path !== '/login' && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router
