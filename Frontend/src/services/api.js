import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Fetch tokenized subtitles for a YouTube URL.
 * @param {string} youtubeUrl
 * @returns {Promise<{videoId: string, subtitles: Array}>}
 */
export async function fetchSubtitles(youtubeUrl) {
  const res = await api.post('/subtitles', { youtubeUrl })
  return res.data
}

/**
 * Translate a word in context.
 * @param {string} word
 * @param {string} sentence
 * @param {string} targetLang
 * @returns {Promise<{translation: string}>}
 */
export async function translateWord(word, sentence, targetLang = 'vi') {
  const res = await api.post('/translate', { word, sentence, targetLang })
  return res.data
}

export async function login(username, password) {
  const res = await api.post('/auth/login', { username, password });
  return res.data;
}

export async function register(username, password) {
  const res = await api.post('/auth/register', { username, password });
  return res.data;
}

export async function saveWord(word, sentence, translation, explanation, targetLang) {
  const res = await api.post('/saved-words', { word, sentence, translation, explanation, targetLang });
  return res.data;
}

export async function getSavedWords() {
  const res = await api.get('/saved-words');
  return res.data;
}
