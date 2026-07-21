<template>
  <div class="panel glass">
    <div v-if="error" class="error-banner">{{ error }}</div>

    <table class="admin-table" v-if="!loading">
      <thead>
        <tr>
          <th>YouTube ID</th>
          <th>Số dòng subtitle</th>
          <th>Hành động</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="v in videos" :key="v.id">
          <td>{{ v.youtubeId }}</td>
          <td>{{ v.subtitleCount }}</td>
          <td>
            <button class="btn btn-sm btn-outline" @click="remove(v)">Xóa khỏi cache</button>
          </td>
        </tr>
        <tr v-if="videos.length === 0">
          <td colspan="3" class="empty">Chưa có video nào được cache.</td>
        </tr>
      </tbody>
    </table>

    <div class="pagination" v-if="totalPages > 1">
      <button class="btn btn-sm btn-outline" :disabled="page === 0" @click="page--; load()">← Trước</button>
      <span>Trang {{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn btn-sm btn-outline" :disabled="page >= totalPages - 1" @click="page++; load()">Sau →</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getVideos, deleteVideo } from '../../services/api.js'

const videos = ref([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getVideos({ page: page.value, size: 20 })
    videos.value = data.content
    totalPages.value = data.totalPages
  } catch (err) {
    error.value = err.response?.data?.error || 'Không tải được danh sách video.'
  } finally {
    loading.value = false
  }
}

async function remove(video) {
  if (!confirm(`Xóa video "${video.youtubeId}" khỏi cache? Lần xem sau sẽ fetch lại từ đầu.`)) return
  try {
    await deleteVideo(video.id)
    await load()
  } catch (err) {
    error.value = err.response?.data?.error || 'Không thể xóa video.'
  }
}

onMounted(load)
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  padding: 24px;
}
.error-banner {
  padding: 12px 16px;
  margin-bottom: 16px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.3);
  border-radius: var(--radius-md);
  color: var(--error);
  font-size: 0.9rem;
}
.admin-table {
  width: 100%;
  border-collapse: collapse;
}
.admin-table th {
  text-align: left;
  padding: 10px 12px;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border);
}
.admin-table td {
  padding: 12px;
  border-bottom: 1px solid var(--border);
  font-size: 0.9rem;
}
.admin-table .empty {
  text-align: center;
  color: var(--text-muted);
  padding: 24px;
}
.pagination {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  font-size: 0.85rem;
  color: var(--text-secondary);
}
</style>
