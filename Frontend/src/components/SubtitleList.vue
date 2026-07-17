<template>
  <div class="subtitle-panel glass" ref="panelRef">
    <div class="panel-header">
      <span class="panel-icon">💬</span>
      <span class="panel-title">Subtitles</span>
      <span class="panel-count">{{ subtitles.length }} lines</span>
    </div>

    <div class="subtitle-list" ref="listRef">
      <TransitionGroup name="sub-fade">
        <div
          v-for="(entry, idx) in subtitles"
          :key="idx"
          :ref="el => setEntryRef(el, idx)"
          class="subtitle-entry"
          :class="{ active: idx === activeIndex }"
        >
          <!-- Timestamp badge -->
          <span class="timestamp">{{ formatTime(entry.start) }}</span>

          <!-- Token words -->
          <div class="tokens">
            <span
              v-for="(token, tIdx) in entry.tokens"
              :key="tIdx"
              class="token"
              :class="{
                'token-word': !isPunct(token),
                'token-punct': isPunct(token),
                'token-active': selectedWord === token && idx === selectedEntryIdx
              }"
              @click.stop="!isPunct(token) && onWordClick($event, token, entry.text, idx)"
            >{{ token }}</span>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  subtitles:   { type: Array,  default: () => [] },
  activeIndex: { type: Number, default: -1 }
})

const emit = defineEmits(['wordClick'])

const panelRef = ref(null)
const listRef  = ref(null)
const entryRefs = ref([])

const selectedWord     = ref('')
const selectedEntryIdx = ref(-1)

function setEntryRef(el, idx) {
  if (el) entryRefs.value[idx] = el
}

// Auto-scroll active subtitle into view
watch(() => props.activeIndex, async (newIdx) => {
  if (newIdx < 0) return
  await nextTick()
  const el = entryRefs.value[newIdx]
  if (el) {
    el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }
})

function isPunct(token) {
  return /^[\p{P}\s]+$/u.test(token)
}

function formatTime(ms) {
  const totalSec = Math.floor(ms / 1000)
  const m = Math.floor(totalSec / 60).toString().padStart(2, '0')
  const s = (totalSec % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

function onWordClick(event, token, sentence, entryIdx) {
  selectedWord.value     = token
  selectedEntryIdx.value = entryIdx

  const rect = event.target.getBoundingClientRect()
  const position = {
    x: rect.left + rect.width / 2,
    y: rect.top
  }

  emit('wordClick', { word: token, sentence, position })
}
</script>

<style scoped>
.subtitle-panel {
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-lg);
  overflow: hidden;
  height: 100%;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.panel-icon { font-size: 1.1rem; }
.panel-title {
  font-family: var(--font-display);
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
}
.panel-count {
  font-size: 0.78rem;
  color: var(--text-muted);
  background: var(--bg-elevated);
  padding: 3px 10px;
  border-radius: 20px;
}

.subtitle-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.subtitle-entry {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  transition: var(--transition);
  border-left: 3px solid transparent;
  cursor: default;
}

.subtitle-entry.active {
  background: linear-gradient(90deg, rgba(108,99,255,0.12) 0%, transparent 100%);
  border-left-color: var(--accent);
}

.timestamp {
  font-size: 0.72rem;
  font-family: monospace;
  color: var(--text-muted);
  flex-shrink: 0;
  padding-top: 4px;
  min-width: 36px;
}

.tokens {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  line-height: 1.8;
}

.token {
  font-size: 0.92rem;
  color: var(--text-secondary);
  transition: var(--transition);
  padding: 1px 3px;
  border-radius: 4px;
}

.token-word {
  cursor: pointer;
  color: var(--text-primary);
}
.token-word:hover {
  color: var(--accent-light);
  background: var(--accent-glow);
  transform: translateY(-1px);
}

.token-punct {
  cursor: default;
  color: var(--text-muted);
}

.token-active {
  color: var(--accent-light) !important;
  background: var(--accent-glow) !important;
}

.subtitle-entry.active .token {
  color: var(--text-primary);
}

/* TransitionGroup */
.sub-fade-enter-active,
.sub-fade-leave-active { transition: opacity 0.3s ease; }
.sub-fade-enter-from,
.sub-fade-leave-to { opacity: 0; }
</style>
