import Cookies from 'js-cookie'

const TokenKey = 'token'

export function getToken() {
  const storedToken = localStorage.getItem(TokenKey)
  if (storedToken !== null) {
    return isUsableValue(storedToken) ? storedToken : null
  }
  const token = Cookies.get(TokenKey)
  return isUsableValue(token) ? token : null
}
export function getUsername() {
  const storedUsername = localStorage.getItem('username')
  if (storedUsername !== null) {
    return isUsableValue(storedUsername) ? storedUsername : null
  }
  const username = Cookies.get('username')
  return isUsableValue(username) ? username : null
}

export function setToken(token) {
  if (!isUsableValue(token)) {
    localStorage.removeItem(TokenKey)
    return Cookies.remove(TokenKey)
  }
  localStorage.setItem(TokenKey, token)
  return Cookies.set(TokenKey, token)
}

export function setUsername(username) {
  if (!isUsableValue(username)) {
    localStorage.removeItem('username')
    return Cookies.remove('username')
  }
  localStorage.setItem('username', username)
  return Cookies.set('username', username)
}

export function removeKey() {
  localStorage.removeItem(TokenKey)
  return Cookies.remove(TokenKey)
}

export function removeUsername() {
  localStorage.removeItem('username')
  return Cookies.remove('username')
}



function isUsableValue(value) {
  return typeof value === 'string' && value.trim() !== '' && value !== 'null' && value !== 'undefined'
}
