<template>
  <div class="panel glass">
    <div class="panel-header">
      <input v-model="search" @input="page = 0; load()" class="input search-input" placeholder="Tìm theo username..." />
    </div>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <table class="admin-table" v-if="!loading">
      <thead>
        <tr>
          <th>Username</th>
          <th>Role</th>
          <th>Trạng thái</th>
          <th>Hành động</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.id">
          <td>{{ u.username }}</td>
          <td>
            <span class="badge" :class="u.role === 'ADMIN' ? 'badge-admin' : 'badge-user'">{{ u.role }}</span>
          </td>
          <td>
            <span class="badge" :class="u.enabled ? 'badge-ok' : 'badge-off'">{{ u.enabled ? 'Đang hoạt động' : 'Đã khóa' }}</span>
          </td>
          <td class="actions">
            <button class="btn btn-sm btn-outline" @click="toggleRole(u)">
              {{ u.role === 'ADMIN' ? 'Hạ xuống USER' : 'Nâng lên ADMIN' }}
            </button>
            <button class="btn btn-sm btn-outline" @click="toggleStatus(u)">
              {{ u.enabled ? 'Khóa' : 'Mở khóa' }}
            </button>
          </td>
        </tr>
        <tr v-if="users.length === 0">
          <td colspan="4" class="empty">Không có người dùng nào.</td>
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
import { getUsers, updateUserRole, updateUserStatus } from '../../services/api.js'

const users = ref([])
const search = ref('')
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getUsers({ search: search.value, page: page.value, size: 20 })
    users.value = data.content
    totalPages.value = data.totalPages
  } catch (err) {
    error.value = err.response?.data?.error || 'Không tải được danh sách người dùng.'
  } finally {
    loading.value = false
  }
}

async function toggleRole(user) {
  const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  if (!confirm(`Đổi vai trò của "${user.username}" thành ${newRole}?`)) return
  try {
    await updateUserRole(user.id, newRole)
    await load()
  } catch (err) {
    error.value = err.response?.data?.error || 'Không thể đổi vai trò.'
  }
}

async function toggleStatus(user) {
  const action = user.enabled ? 'khóa' : 'mở khóa'
  if (!confirm(`Bạn có chắc muốn ${action} tài khoản "${user.username}"?`)) return
  try {
    await updateUserStatus(user.id, !user.enabled)
    await load()
  } catch (err) {
    error.value = err.response?.data?.error || 'Không thể thay đổi trạng thái.'
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
.admin-table .empty {
  text-align: center;
  color: var(--text-muted);
  padding: 24px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 600;
}
.badge-admin { background: rgba(108, 99, 255, 0.15); color: var(--accent-light); }
.badge-user { background: rgba(155, 163, 192, 0.15); color: var(--text-secondary); }
.badge-ok { background: rgba(34, 211, 160, 0.15); color: var(--success); }
.badge-off { background: rgba(248, 113, 113, 0.15); color: var(--error); }
.pagination {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  font-size: 0.85rem;
  color: var(--text-secondary);
}
</style>
