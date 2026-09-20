export const AUTH_STORAGE_KEYS = {
  TOKEN: 'schedule_auth_token',
  USER: 'schedule_auth_user',
}

export const getStoredToken = () => {
  return localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ?? sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)
}
