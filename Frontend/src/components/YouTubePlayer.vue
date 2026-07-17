<template>
  <div class="player-wrapper">
    <div class="player-frame glass" :id="containerId"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  videoId: { type: String, required: true }
})

const emit = defineEmits(['timeUpdate'])

const containerId = 'yt-player-container'
let player = null
let pollInterval = null

// Poll player currentTime every 300ms
function startPolling() {
  stopPolling()
  pollInterval = setInterval(() => {
    if (player && typeof player.getCurrentTime === 'function') {
      try {
        const timeSec = player.getCurrentTime()
        emit('timeUpdate', Math.round(timeSec * 1000)) // emit ms
      } catch (_) {}
    }
  }, 300)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

function createPlayer(videoId) {
  if (player) {
    try { player.destroy() } catch (_) {}
    player = null
  }
  stopPolling()

  player = new window.YT.Player(containerId, {
    videoId,
    width: '100%',
    height: '100%',
    playerVars: {
      autoplay: 0,
      modestbranding: 1,
      rel: 0,
      cc_load_policy: 0,
    },
    events: {
      onReady: () => {
        startPolling()
      },
      onStateChange: (event) => {
        if (event.data === window.YT.PlayerState.PLAYING) {
          startPolling()
        } else {
          stopPolling()
          // Still emit current time on pause
          if (player && typeof player.getCurrentTime === 'function') {
            emit('timeUpdate', Math.round(player.getCurrentTime() * 1000))
          }
        }
      }
    }
  })
}

function initPlayer() {
  if (window.YT && window.YT.Player) {
    createPlayer(props.videoId)
  } else {
    // YouTube API not ready yet – set callback
    window.onYouTubeIframeAPIReady = () => createPlayer(props.videoId)
  }
}

onMounted(() => {
  initPlayer()
})

watch(() => props.videoId, (newId) => {
  if (newId) {
    if (player && typeof player.loadVideoById === 'function') {
      player.loadVideoById(newId)
    } else {
      initPlayer()
    }
  }
})

onUnmounted(() => {
  stopPolling()
  if (player) {
    try { player.destroy() } catch (_) {}
    player = null
  }
})
</script>

<style scoped>
.player-wrapper {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6), 0 0 0 1px var(--border);
}
.player-frame {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: none;
}
/* Override iframe injected by YouTube API */
:deep(iframe) {
  width: 100% !important;
  height: 100% !important;
  border: none !important;
  border-radius: var(--radius-lg);
}
</style>
