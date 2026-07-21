<template>
  <div class="admin-app">
    <header class="header glass">
      <div class="container header-inner">
        <div class="logo">
          <span class="logo-icon">▶</span>
          <span class="logo-text gradient-text">SubLearn CMS</span>
        </div>
        <div class="header-controls">
          <router-link to="/" class="btn btn-sm btn-outline">← Về trang chính</router-link>
        </div>
      </div>
    </header>

    <main class="container main">
      <nav class="tabs glass">
        <button :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">Người dùng</button>
        <button :class="{ active: activeTab === 'videos' }" @click="activeTab = 'videos'">Video / Subtitle</button>
        <button :class="{ active: activeTab === 'translations' }" @click="activeTab = 'translations'">Bản dịch</button>
        <button :class="{ active: activeTab === 'errorLogs' }" @click="activeTab = 'errorLogs'">Log lỗi</button>
      </nav>

      <section class="tab-content">
        <AdminUsers v-if="activeTab === 'users'" />
        <AdminVideos v-else-if="activeTab === 'videos'" />
        <AdminTranslations v-else-if="activeTab === 'translations'" />
        <AdminErrorLogs v-else-if="activeTab === 'errorLogs'" />
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AdminUsers from '../components/admin/AdminUsers.vue'
import AdminVideos from '../components/admin/AdminVideos.vue'
import AdminTranslations from '../components/admin/AdminTranslations.vue'
import AdminErrorLogs from '../components/admin/AdminErrorLogs.vue'

const activeTab = ref('users')
</script>

<style scoped>
.admin-app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid var(--border);
}
.header-inner {
  padding: 14px 15px;
  display: flex;
  align-items: center;
  gap: 20px;
}
.header-controls {
  margin-left: auto;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo-icon {
  font-size: 1.2rem;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.logo-text {
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 700;
}
.main {
  flex: 1;
  padding: 32px 15px 60px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.tabs {
  display: flex;
  gap: 4px;
  padding: 6px;
  border-radius: var(--radius-md);
  width: fit-content;
}
.tabs button {
  background: none;
  border: none;
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
}
.tabs button.active {
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-2) 100%);
  color: #fff;
}
.tab-content {
  flex: 1;
}
</style>
