import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import DashBoard from '../views/DashBoard.vue'
import InboundOrder from '../views/InboundOrder.vue'
import Receipt from '../views/Receipt.vue'
import Putaway from "../views/Putaway.vue";

const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: '/login',
            component: Login,
            meta: { guestOnly: true },
        },
        {
            path: '/',
            component: Layout,
            meta: { requiresAuth: true },
            children: [
                {
                    path: '',
                    redirect: '/dashboard',
                },
                {
                    path: 'dashboard',
                    component: DashBoard,
                    meta: { title: '工作台' },
                },
                {
                    path: 'inbound/orders',
                    component: InboundOrder,
                    meta: { title: '入库单管理' },
                },
                {
                    path: 'inbound/receipt',
                    component: Receipt,
                    meta: {title: '收货管理'}
                },
                {
                    path: 'inbound/putaway',
                    component: Putaway,
                    meta: {title: '上架管理'}
                }
            ],
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