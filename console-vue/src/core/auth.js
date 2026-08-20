import Cookies from 'js-cookie'

const TokenKey = 'token'
const UsernameKey = 'username'

export function getToken() {
  return readValue(TokenKey)
}

export function getUsername() {
  return readValue(UsernameKey)
}

export function setToken(token) {
  return writeValue(TokenKey, token)
}

export function setUsername(username) {
  return writeValue(UsernameKey, username)
}

export function removeKey() {
  return removeValue(TokenKey)
}

export function removeUsername() {
  return removeValue(UsernameKey)
}

export function isUsableValue(value) {
  return typeof value === 'string'
    && value.trim() !== ''
    && value !== 'null'
    && value !== 'undefined'
}

function readValue(key) {
  const storedValue = localStorage.getItem(key)
  if (isUsableValue(storedValue)) {
    return storedValue
  }

  const cookieValue = Cookies.get(key)
  if (isUsableValue(cookieValue)) {
    // Reconcile a missing or stale local value with the valid cookie fallback.
    localStorage.setItem(key, cookieValue)
    return cookieValue
  }

  if (storedValue !== null) {
    localStorage.removeItem(key)
  }
  return null
}

function writeValue(key, value) {
  if (!isUsableValue(value)) {
    return removeValue(key)
  }

  localStorage.setItem(key, value)
  return Cookies.set(key, value)
}

function removeValue(key) {
  localStorage.removeItem(key)
  return Cookies.remove(key)
}
