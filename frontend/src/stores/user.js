import { defineStore } from 'pinia'

function getSavedUser() {
    try {
        return JSON.parse(localStorage.getItem('wmsUser')) || {}
    } catch {
        return {}
    }
}

export const useUserStore = defineStore('user', {
    state: () => ({
        token: '',
        userId: null,
        username: '',
        realName: '',

        warehouseId: null,
        warehouseName: '',

        ...getSavedUser(),
    }),

    getters: {
        isLoggedIn: (state) => Boolean(state.token && state.userId),
    },

    actions: {
        // 登录成功
        setUser(data) {
            this.token = data.token
            this.userId = data.id
            this.username = data.username
            this.realName = data.realName
            this.warehouseId = data.defaultWarehouseId

            this.save()
        },

        // Dashboard 获取仓库信息后调用
        setWarehouse(data) {
            this.warehouseId = data.warehouseId
            this.warehouseName = data.warehouseName

            this.save()
        },

        save() {
            localStorage.setItem(
                'wmsUser',
                JSON.stringify({
                    token: this.token,
                    userId: this.userId,
                    username: this.username,
                    realName: this.realName,
                    warehouseId: this.warehouseId,
                    warehouseName: this.warehouseName,
                })
            )
        },

        logout() {
            localStorage.removeItem('wmsUser')
            this.$reset()
        },
    },
})