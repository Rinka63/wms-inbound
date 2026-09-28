import {
    createRouter,
    createWebHashHistory,
} from 'vue-router'

import Login from '../views/Login.vue'
import DashBoard from '../views/DashBoard.vue'

import { useUserStore } from '../stores/user'

const router = createRouter({
    history: createWebHashHistory(),

    routes: [
        {
            path: '/',
            redirect: '/dashboard',
        },

        {
            path: '/login',
            component: Login,
        },

        {
            path: '/dashboard',
            component: DashBoard,
            meta: {
                requiresAuth: true,
            },
        },
    ],
})

router.beforeEach((to) => {
    const userStore = useUserStore()

    // 没登录却访问 Dashboard
    if (to.meta.requiresAuth && !userStore.isLoggedIn) {
        return '/login'
    }

    // 已登录又进入登录页
    if (to.path === '/login' && userStore.isLoggedIn) {
        return '/dashboard'
    }
})

export default router