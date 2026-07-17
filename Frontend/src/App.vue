<template>
  <div class="app" @click="closeTooltip">

    <!-- ── Header ── -->
    <header class="header glass">
      <div class="container header-inner">
        <div class="logo">
          <span class="logo-icon">▶</span>
          <span class="logo-text gradient-text">SubLearn</span>
        </div>
        <p class="header-tagline">Learn English by clicking words in YouTube subtitles</p>
        
        <div class="header-controls">
          <template v-if="currentUser">
            <span class="user-greeting">Hi, {{ currentUser }}</span>
            <button class="btn btn-sm btn-primary" @click="showSavedWordsModal = true">Saved Words</button>
            <button class="btn btn-sm btn-outline" @click="logout">Logout</button>
          </template>
          <template v-else>
            <button class="btn btn-sm btn-primary" @click="showAuthModal = true">Login / Register</button>
          </template>
        </div>
      </div>
    </header>

    <!-- ── Main ── -->
    <main class="container main">

      <!-- Input section -->
      <section class="search-section fade-in-up">
        <div class="search-card glass">
          <h1 class="search-title">
            Paste a YouTube link to begin
          </h1>
          <p class="search-desc">
            We'll fetch the English subtitles, split them word-by-word, and let you click any word for an instant AI translation.
          </p>

          <form class="search-form" @submit.prevent="loadSubtitles">
            <div class="input-wrapper">
              <span class="input-icon">🔗</span>
              <input
                id="youtube-url-input"
                v-model="youtubeUrl"
                type="url"
                class="input url-input"
                placeholder="https://www.youtube.com/watch?v=..."
                autocomplete="off"
                spellcheck="false"
                :disabled="loading"
              />
            </div>
            
            <div class="lang-select-wrapper">
              <select v-model="targetLang" class="input lang-select" :disabled="loading">
                <option value="vi">Vietnamese</option>
                <option value="es">Spanish</option>
                <option value="fr">French</option>
                <option value="de">German</option>
                <option value="ja">Japanese</option>
                <option value="ko">Korean</option>
                <option value="zh">Chinese</option>
              </select>
            </div>

            <button
              id="load-subtitles-btn"
              type="submit"
              class="btn btn-primary"
              :disabled="loading || !youtubeUrl.trim()"
            >
              <span v-if="loading" class="spinner-small"></span>
              <span v-else>✦</span>
              {{ loading ? 'Loading…' : 'Load Subtitles' }}
            </button>
          </form>

          <!-- Error message -->
          <Transition name="slide-down">
            <div v-if="fetchError" class="error-banner">
              <span>⚠</span> {{ fetchError }}
            </div>
          </Transition>
        </div>
      </section>

      <!-- Player + Subtitle section -->
      <Transition name="panel-fade">
        <section v-if="videoId" class="row content-section fade-in-up">

          <!-- YouTube Player -->
          <div class="col-lg-8 player-col">
            <YouTubePlayer
              :videoId="videoId"
              @timeUpdate="onTimeUpdate"
            />

            <!-- Stats bar -->
            <div class="stats-bar glass mt-3">
              <div class="stat">
                <span class="stat-label">Lines</span>
                <span class="stat-value">{{ subtitles.length }}</span>
              </div>
              <div class="stat-divider"></div>
              <div class="stat">
                <span class="stat-label">Active</span>
                <span class="stat-value">{{ activeIndex >= 0 ? activeIndex + 1 : '—' }}</span>
              </div>
              <div class="stat-divider"></div>
              <div class="stat">
                <span class="stat-label">Translations</span>
                <span class="stat-value">{{ translationCount }}</span>
              </div>
            </div>

            <!-- Tip -->
            <div class="tip-card glass mt-3 mb-4 mb-lg-0">
              <span class="tip-icon">💡</span>
              <span>Click any <strong>word</strong> in the subtitle panel to see its Vietnamese translation in context.</span>
            </div>
          </div>

          <!-- Subtitle panel -->
          <div class="col-lg-4 subtitle-col">
            <SubtitleList
              :subtitles="subtitles"
              :activeIndex="activeIndex"
              @wordClick="onWordClick"
            />
          </div>

        </section>
      </Transition>

    </main>

    <!-- Translation Tooltip -->
    <TranslationTooltip
      :visible="tooltip.visible"
      :word="tooltip.word"
      :sentence="tooltip.sentence"
      :translation="tooltip.translation"
      :explanation="tooltip.explanation"
      :targetLang="targetLang"
      :loading="tooltip.loading"
      :error="tooltip.error"
      :position="tooltip.position"
      :isLoggedIn="!!currentUser"
    />

    <AuthModal 
      :visible="showAuthModal" 
      @close="showAuthModal = false" 
      @auth-success="onAuthSuccess" 
    />
    
    <SavedWordsModal 
      :visible="showSavedWordsModal" 
      @close="showSavedWordsModal = false" 
    />

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import YouTubePlayer       from './components/YouTubePlayer.vue'
import SubtitleList        from './components/SubtitleList.vue'
import TranslationTooltip  from './components/TranslationTooltip.vue'
import AuthModal           from './components/AuthModal.vue'
import SavedWordsModal     from './components/SavedWordsModal.vue'
import { fetchSubtitles, translateWord } from './services/api.js'

// ── State ──────────────────────────────────────────────────
const youtubeUrl     = ref('')
const loading        = ref(false)
const fetchError     = ref('')
const targetLang     = ref('vi')

const currentUser    = ref(null)
const showAuthModal  = ref(false)
const showSavedWordsModal = ref(false)

const videoId        = ref('')
const subtitles      = ref([])  // [{start, end, text, tokens}]
const currentTimeMs  = ref(0)
const translationCount = ref(0)

// Active subtitle index based on current time
const activeIndex = computed(() => {
  const t = currentTimeMs.value
  for (let i = subtitles.value.length - 1; i >= 0; i--) {
    const s = subtitles.value[i]
    if (t >= s.start && t <= s.end + 500) return i
  }
  return -1
})

// Tooltip state
const tooltip = ref({
  visible:     false,
  word:        '',
  sentence:    '',
  translation: '',
  explanation: '',
  loading:     false,
  error:       '',
  position:    { x: 0, y: 0 }
})

// Active translation request (for debounce/cancel)
let translateAbortRef = { cancel: false }

onMounted(() => {
  const username = localStorage.getItem('username');
  if (username) {
    currentUser.value = username;
  }
})

// ── Actions ────────────────────────────────────────────────

async function loadSubtitles() {
  const url = youtubeUrl.value.trim()
  if (!url) return

  loading.value    = true
  fetchError.value = ''
  videoId.value    = ''
  subtitles.value  = []
  closeTooltip()

  try {
    const data = await fetchSubtitles(url)
    videoId.value   = data.videoId
    subtitles.value = data.subtitles
    if (!data.subtitles || data.subtitles.length === 0) {
      fetchError.value = 'Video này không có phụ đề tiếng Anh.'
      videoId.value = ''
    }
  } catch (err) {
    const msg = err?.response?.data?.error
    fetchError.value = msg || 'Không thể tải phụ đề. Hãy thử video khác.'
    videoId.value = ''
  } finally {
    loading.value = false
  }
}

function onTimeUpdate(ms) {
  currentTimeMs.value = ms
}

async function onWordClick({ word, sentence, position }) {
  // Cancel any previous pending request
  translateAbortRef.cancel = true
  const abortRef = { cancel: false }
  translateAbortRef = abortRef

  tooltip.value = {
    visible:     true,
    word,
    sentence,
    translation: '',
    explanation: '',
    loading:     true,
    error:       '',
    position
  }

  try {
    const data = await translateWord(word, sentence, targetLang.value)
    if (abortRef.cancel) return  // newer click came in

    tooltip.value.translation = data.translation
    tooltip.value.explanation = data.explanation
    tooltip.value.loading     = false
    translationCount.value++
  } catch (err) {
    if (abortRef.cancel) return
    tooltip.value.error   = 'Không dịch được, thử lại.'
    tooltip.value.loading = false
  }
}

function closeTooltip() {
  tooltip.value.visible = false
  translateAbortRef.cancel = true
}

function onAuthSuccess(username) {
  currentUser.value = username;
}

function logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  currentUser.value = null;
}
</script>

<style scoped>
/* ── App shell ── */
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ── Header ── */
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
  display: flex;
  align-items: center;
  gap: 15px;
}
.user-greeting {
  font-size: 0.9rem;
  color: var(--text-secondary);
  font-weight: 500;
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
  font-size: 1.4rem;
  font-weight: 700;
}
.header-tagline {
  font-size: 0.82rem;
  color: var(--text-muted);
  border-left: 1px solid var(--border);
  padding-left: 20px;
}

/* ── Main ── */
.main {
  flex: 1;
  padding: 40px 15px 60px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* ── Search section ── */
.search-section { width: 100%; }
.search-card {
  border-radius: var(--radius-xl);
  padding: 40px 48px;
  text-align: center;
}
.search-title {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 12px;
  background: linear-gradient(135deg, var(--text-primary) 0%, var(--text-secondary) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.search-desc {
  font-size: 0.95rem;
  color: var(--text-secondary);
  margin-bottom: 28px;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}
.search-form {
  display: flex;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto;
  flex-wrap: wrap;
}
.input-wrapper {
  position: relative;
  flex: 2;
  min-width: 250px;
}
.lang-select-wrapper {
  flex: 1;
  min-width: 120px;
}
.lang-select {
  width: 100%;
}
.input-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 1rem;
  pointer-events: none;
}
.url-input {
  padding-left: 46px;
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
.error-banner {
  margin-top: 16px;
  padding: 12px 20px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.3);
  border-radius: var(--radius-md);
  color: var(--error);
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 700px;
  margin-left: auto;
  margin-right: auto;
  text-align: left;
}

/* ── Content section ── */
.content-section {
  min-height: 600px;
}

/* Player column */
.player-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 90px;
}

/* Stats bar */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  border-radius: var(--radius-md);
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-label {
  font-size: 0.68rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.stat-value {
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--accent-light);
}
.stat-divider {
  width: 1px;
  height: 28px;
  background: var(--border);
}

/* Tip card */
.tip-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.5;
}
.tip-icon { font-size: 1rem; flex-shrink: 0; }
.tip-card strong { color: var(--accent-light); }

/* Subtitle column */
.subtitle-col {
  height: calc(100vh - 200px);
  max-height: 700px;
}

/* ── Transitions ── */
.slide-down-enter-active { animation: fadeInUp 0.3s ease; }
.slide-down-leave-active { animation: fadeInUp 0.2s ease reverse; }

.panel-fade-enter-active { animation: fadeInUp 0.5s ease; }
.panel-fade-leave-active { animation: fadeIn 0.2s ease reverse; }
</style>
