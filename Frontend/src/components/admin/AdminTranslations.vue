<template>
  <div class="panel glass">
    <div class="panel-header">
      <input v-model="word" @input="page = 0; load()" class="input search-input" placeholder="Tìm theo từ..." />
    </div>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <table class="admin-table" v-if="!loading">
      <thead>
        <tr>
          <th>Từ</th>
          <th>Câu</th>
          <th>Ngôn ngữ</th>
          <th>Bản dịch</th>
          <th>Hành động</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="t in translations" :key="t.id">
          <td>{{ t.word }}</td>
          <td class="sentence-cell">{{ t.sentence }}</td>
          <td>{{ t.targetLang }}</td>
          <td>{{ t.translatedText }}</td>
          <td>
            <button class="btn btn-sm btn-outline" @click="remove(t)">Xóa</button>
          </td>
        </tr>
        <tr v-if="translations.length === 0">
          <td colspan="5" class="empty">Chưa có bản dịch nào được cache.</td>
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
import { getTranslations, deleteTranslation } from '../../services/api.js'

const translations = ref([])
const word = ref('')
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getTranslations({ word: word.value, page: page.value, size: 20 })
    translations.value = data.content
    totalPages.value = data.totalPages
  } catch (err) {
    error.value = err.response?.data?.error || 'Không tải được danh sách bản dịch.'
  } finally {
    loading.value = false
  }
}

async function remove(translation) {
  if (!confirm(`Xóa bản dịch của từ "${translation.word}"?`)) return
  try {
    await deleteTranslation(translation.id)
    await load()
  } catch (err) {
    error.value = err.response?.data?.error || 'Không thể xóa bản dịch.'
  }
}

onMounted(load)
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  padding: 24px;
}
.panel-header {
  margin-bottom: 16px;
}
.search-input {
  max-width: 320px;
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
.sentence-cell {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
