<script setup>

import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import Login from "./views/Login.vue";
import Dashboard from './views/DashBoard.vue'

// const currentPath = ref(window.location.hash || '#/login')
//
// function syncRoute() {
//   currentPath.value = window.location.hash || '#/login'
// }
//
// onMounted(() => {
//   window.addEventListener('hashchange', syncRoute)
// })
//
// onBeforeUnmount(() => {
//   window.removeEventListener('hashchange', syncRoute)
// })
//
// const isLoggedIn = computed(() => {
//   return Boolean(localStorage.getItem('wmsUser'))
// })



</script>

<template>
<!--  <Dashboard v-if="isLoggedIn && currentPath === '#/dashboard'"/>-->

<!--  <Login v-else/>-->
  <div class="page-content">
    <RouterView />
  </div>

</template>
