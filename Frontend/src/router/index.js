import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import AdminView from '../views/AdminView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/admin', component: AdminView }
  ]
})

router.beforeEach((to) => {
  if (to.path === '/admin' && localStorage.getItem('role') !== 'ADMIN') {
    return '/'
  }
})

export default router
