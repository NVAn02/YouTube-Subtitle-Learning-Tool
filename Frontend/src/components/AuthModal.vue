<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="visible" class="modal-overlay" @click.self="close">
        <div class="modal-content glass">
          <button class="close-btn" @click="close">&times;</button>
          
          <div class="tabs">
            <button :class="{ active: isLogin }" @click="isLogin = true">Login</button>
            <button :class="{ active: !isLogin }" @click="isLogin = false">Register</button>
          </div>

          <form @submit.prevent="handleSubmit" class="auth-form">
            <div class="input-group">
              <label>Username</label>
              <input v-model="username" type="text" required class="input" />
            </div>
            <div class="input-group">
              <label>Password</label>
              <input v-model="password" type="password" required class="input" />
            </div>

            <div v-if="error" class="error-text">{{ error }}</div>

            <button type="submit" class="btn btn-primary w-100 mt-3" :disabled="loading">
              <span v-if="loading" class="spinner-small"></span>
              <span v-else>{{ isLogin ? 'Login' : 'Register' }}</span>
            </button>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue';
import { login, register } from '../services/api.js';

const props = defineProps({
  visible: Boolean
});

const emit = defineEmits(['close', 'auth-success']);

const isLogin = ref(true);
const username = ref('');
const password = ref('');
const error = ref('');
const loading = ref(false);

watch(() => props.visible, (val) => {
  if (val) {
    username.value = '';
    password.value = '';
    error.value = '';
  }
});

const close = () => emit('close');

const handleSubmit = async () => {
  error.value = '';
  loading.value = true;
  try {
    let data;
    if (isLogin.value) {
      data = await login(username.value, password.value);
    } else {
      data = await register(username.value, password.value);
    }
    localStorage.setItem('token', data.token);
    localStorage.setItem('username', data.username);
    emit('auth-success', data.username);
    close();
  } catch (err) {
    error.value = err.response?.data?.error || 'Authentication failed';
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}
.modal-content {
  position: relative;
  width: 90%;
  max-width: 400px;
  padding: 30px;
  border-radius: var(--radius-lg);
}
.close-btn {
  position: absolute;
  top: 15px;
  right: 15px;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 1.5rem;
  cursor: pointer;
}
.tabs {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border);
}
.tabs button {
  background: none;
  border: none;
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.tabs button.active {
  color: var(--accent-light);
  border-bottom-color: var(--accent-light);
}
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.input-group label {
  display: block;
  margin-bottom: 5px;
  font-size: 0.9rem;
  color: var(--text-secondary);
}
.input-group input {
  width: 100%;
}
.error-text {
  color: var(--error);
  font-size: 0.9rem;
  margin-top: -5px;
}
.spinner-small {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.2s; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
</style>
