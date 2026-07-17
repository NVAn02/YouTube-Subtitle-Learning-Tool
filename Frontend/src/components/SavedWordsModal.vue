<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="visible" class="modal-overlay" @click.self="close">
        <div class="modal-content glass">
          <button class="close-btn" @click="close">&times;</button>
          
          <h2 class="title">Saved Words</h2>

          <div v-if="loading" class="text-center mt-4">Loading...</div>
          
          <div v-else-if="words.length === 0" class="empty-state">
            No words saved yet.
          </div>

          <div v-else class="words-list">
            <div v-for="w in words" :key="w.id" class="word-card">
              <div class="word-header">
                <span class="word">{{ w.word }}</span>
                <span class="lang-badge">{{ w.targetLang.toUpperCase() }}</span>
              </div>
              <div class="translation">{{ w.translation }}</div>
              <div class="explanation" v-if="w.explanation">{{ w.explanation }}</div>
              <div class="sentence">"{{ w.sentence }}"</div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue';
import { getSavedWords } from '../services/api.js';

const props = defineProps({
  visible: Boolean
});

const emit = defineEmits(['close']);
const close = () => emit('close');

const words = ref([]);
const loading = ref(false);

watch(() => props.visible, async (val) => {
  if (val) {
    loading.value = true;
    try {
      words.value = await getSavedWords();
    } catch (e) {
      console.error(e);
    } finally {
      loading.value = false;
    }
  }
});
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
  max-width: 600px;
  max-height: 80vh;
  padding: 30px;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
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
.title {
  margin-top: 0;
  margin-bottom: 20px;
  font-family: var(--font-display);
}
.empty-state {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
}
.words-list {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding-right: 10px;
}
.word-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 15px;
}
.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}
.word {
  font-size: 1.2rem;
  font-weight: bold;
  color: var(--accent-light);
}
.lang-badge {
  font-size: 0.7rem;
  background: var(--accent);
  padding: 2px 6px;
  border-radius: 4px;
}
.translation {
  font-size: 1.1rem;
  margin-bottom: 8px;
}
.explanation {
  font-size: 0.9rem;
  color: var(--text-secondary);
  background: rgba(0,0,0,0.2);
  padding: 8px;
  border-radius: 4px;
  margin-bottom: 10px;
}
.sentence {
  font-size: 0.85rem;
  font-style: italic;
  color: var(--text-muted);
}
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.2s; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
</style>
