export const AUTH_STORAGE_KEYS = {
  TOKEN: 'schedule_auth_token',
  USER: 'schedule_auth_user',
}

export const getStoredToken = () => {
  return sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ?? localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)
}

export const updateStoredToken = (accessToken: string) => {
  const storage = sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ? sessionStorage : localStorage
  storage.setItem(AUTH_STORAGE_KEYS.TOKEN, accessToken)
}
