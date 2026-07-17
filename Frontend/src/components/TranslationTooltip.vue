<template>
  <!-- Tooltip rendered as a fixed-position popup -->
  <Teleport to="body">
    <Transition name="tooltip-pop">
      <div
        v-if="visible"
        class="translation-tooltip glass"
        :style="tooltipStyle"
        @click.stop
      >
        <div class="tooltip-word">{{ word }}</div>
        <div class="tooltip-divider"></div>

        <div v-if="loading" class="tooltip-loading">
          <span class="spinner"></span>
          <span>Đang dịch…</span>
        </div>

        <div v-else-if="error" class="tooltip-error">
          <span class="error-icon">⚠</span>
          {{ error }}
        </div>

        <div v-else class="tooltip-content">
          <div class="tooltip-translation">{{ translation }}</div>
          <div v-if="explanation" class="tooltip-explanation">{{ explanation }}</div>
          
          <div class="tooltip-actions">
            <button 
              v-if="isLoggedIn" 
              class="btn btn-sm btn-primary save-btn" 
              @click.stop="handleSaveWord"
              :disabled="saving"
            >
              {{ saving ? 'Saving...' : 'Save Word' }}
            </button>
            <span v-else class="tooltip-login-hint">Login to save</span>
            <span v-if="saveSuccess" class="save-success">✓ Saved</span>
            <span v-if="saveError" class="save-error">{{ saveError }}</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { saveWord as apiSaveWord } from '../services/api.js'

const props = defineProps({
  visible:     { type: Boolean, default: false },
  word:        { type: String,  default: '' },
  sentence:    { type: String,  default: '' },
  translation: { type: String,  default: '' },
  explanation: { type: String,  default: '' },
  targetLang:  { type: String,  default: 'vi' },
  loading:     { type: Boolean, default: false },
  error:       { type: String,  default: '' },
  position:    { type: Object,  default: () => ({ x: 0, y: 0 }) },
  isLoggedIn:  { type: Boolean, default: false }
})

const saving = ref(false)
const saveSuccess = ref(false)
const saveError = ref('')

watch(() => props.visible, (val) => {
  if (val) {
    saveSuccess.value = false;
    saveError.value = '';
  }
})

const handleSaveWord = async () => {
  saving.value = true;
  saveError.value = '';
  try {
    await apiSaveWord(props.word, props.sentence, props.translation, props.explanation, props.targetLang);
    saveSuccess.value = true;
  } catch (err) {
    saveError.value = err.response?.data?.error || 'Failed to save';
  } finally {
    saving.value = false;
  }
}

// Place tooltip above the clicked word
const tooltipStyle = computed(() => {
  const GAP = 10
  return {
    left: `${props.position.x}px`,
    top:  `${props.position.y - GAP}px`,
    transform: 'translate(-50%, -100%)'
  }
})
</script>

<style scoped>
.translation-tooltip {
  position: fixed;
  z-index: 9999;
  min-width: 120px;
  max-width: 260px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-active);
  box-shadow: 0 8px 32px rgba(0,0,0,0.5), 0 0 0 1px var(--accent-glow);
  pointer-events: auto;
  user-select: none;
}

.tooltip-word {
  font-family: var(--font-display);
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--accent-light);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.tooltip-divider {
  height: 1px;
  background: var(--border);
  margin: 8px 0;
}

.tooltip-translation {
  font-size: 1rem;
  font-weight: 500;
  color: var(--text-primary);
  animation: fadeIn 0.2s ease;
  margin-bottom: 5px;
}

.tooltip-explanation {
  font-size: 0.85rem;
  color: var(--text-secondary);
  font-style: italic;
  margin-bottom: 10px;
}

.tooltip-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  border-top: 1px solid var(--border);
  padding-top: 10px;
}

.save-btn {
  padding: 4px 10px;
  font-size: 0.75rem;
}

.tooltip-login-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.save-success {
  font-size: 0.75rem;
  color: #4ade80;
}

.save-error {
  font-size: 0.75rem;
  color: var(--error);
}

.tooltip-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

.tooltip-error {
  font-size: 0.85rem;
  color: var(--error);
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Transition */
.tooltip-pop-enter-active { animation: fadeInUp 0.2s ease; }
.tooltip-pop-leave-active { animation: fadeIn 0.15s ease reverse; }
</style>
