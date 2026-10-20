import { createRouter, createWebHistory } from 'vue-router'
import routes from './routes'
import { setupGuards } from './guard'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

setupGuards(router)

export default router