<template>
  <div class="panel glass">
    <div class="filters">
      <select v-model="severity" @change="page = 0; load()" class="input filter-input">
        <option value="">Tất cả mức độ</option>
        <option value="WARN">WARN</option>
        <option value="ERROR">ERROR</option>
      </select>
      <input v-model="source" @input="page = 0; load()" class="input filter-input" placeholder="Nguồn (VD: SUBTITLE_FETCH)" />
      <input v-model="from" @change="page = 0; load()" type="datetime-local" class="input filter-input" />
      <input v-model="to" @change="page = 0; load()" type="datetime-local" class="input filter-input" />
      <button class="btn btn-sm btn-outline" @click="deleteMatchingFilter">Xóa theo bộ lọc</button>
    </div>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <table class="admin-table" v-if="!loading">
      <thead>
        <tr>
          <th></th>
          <th>Thời điểm</th>
          <th>Mức độ</th>
          <th>Nguồn</th>
          <th>Thông điệp</th>
          <th>Hành động</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="log in logs" :key="log.id">
          <tr>
            <td>
              <button class="expand-btn" @click="toggleExpand(log.id)">{{ expanded.has(log.id) ? '▾' : '▸' }}</button>
            </td>
            <td>{{ formatDate(log.occurredAt) }}</td>
            <td>
              <span class="badge" :class="log.severity === 'ERROR' ? 'badge-error' : 'badge-warn'">{{ log.severity }}</span>
            </td>
            <td>{{ log.source }}</td>
            <td class="message-cell">{{ log.message }}</td>
            <td>
              <button class="btn btn-sm btn-outline" @click="remove(log)">Xóa</button>
            </td>
          </tr>
          <tr v-if="expanded.has(log.id)">
            <td colspan="6" class="detail-cell">
              <div v-if="log.context"><strong>Context:</strong> {{ log.context }}</div>
              <pre v-if="log.stackTrace" class="stack-trace">{{ log.stackTrace }}</pre>
            </td>
          </tr>
        </template>
        <tr v-if="logs.length === 0">
          <td colspan="6" class="empty">Không có log lỗi nào.</td>
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
import { getErrorLogs, deleteErrorLog, deleteErrorLogsMatching } from '../../services/api.js'

const logs = ref([])
const severity = ref('')
const source = ref('')
const from = ref('')
const to = ref('')
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')
const expanded = ref(new Set())

function buildParams() {
  const params = { page: page.value, size: 20 }
  if (severity.value) params.severity = severity.value
  if (source.value) params.source = source.value
  if (from.value) params.from = from.value
  if (to.value) params.to = to.value
  return params
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getErrorLogs(buildParams())
    logs.value = data.content
    totalPages.value = data.totalPages
  } catch (err) {
    error.value = err.response?.data?.error || 'Không tải được danh sách log lỗi.'
  } finally {
    loading.value = false
  }
}

function toggleExpand(id) {
  if (expanded.value.has(id)) {
    expanded.value.delete(id)
  } else {
    expanded.value.add(id)
  }
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}

async function remove(log) {
  if (!confirm('Xóa log lỗi này?')) return
  try {
    await deleteErrorLog(log.id)
    await load()
  } catch (err) {
    error.value = err.response?.data?.error || 'Không thể xóa log.'
  }
}

async function deleteMatchingFilter() {
  if (!confirm('Xóa TẤT CẢ log lỗi khớp với bộ lọc hiện tại? Hành động này không thể hoàn tác.')) return
  try {
    await axios.delete('/api/admin/error-logs', {
      params: buildParams(),
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
    })
    page.value = 0
    await load()
  } catch (err) {
    error.value = err.response?.data?.error || 'Không thể xóa theo bộ lọc.'
  }
}

onMounted(load)
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  padding: 24px;
}
.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.filter-input {
  width: auto;
  min-width: 160px;
  flex: 1;
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
  vertical-align: top;
}
.message-cell {
  max-width: 400px;
}
.expand-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 0.9rem;
}
.detail-cell {
  background: rgba(255,255,255,0.02);
}
.stack-trace {
  margin-top: 8px;
  padding: 12px;
  background: var(--bg-card);
  border-radius: var(--radius-sm);
  font-size: 0.78rem;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}
.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 600;
}
.badge-error { background: rgba(248, 113, 113, 0.15); color: var(--error); }
.badge-warn { background: rgba(245, 158, 11, 0.15); color: var(--warning); }
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
