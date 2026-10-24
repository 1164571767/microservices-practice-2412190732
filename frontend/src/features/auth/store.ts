import { defineStore } from 'pinia'
import { computed, shallowRef } from 'vue'
import { authSession, queryClient } from '../../app/services'
import type { LoginRequest, UserProfile } from '../../shared/api/types'

export const useAuthStore = defineStore('auth', () => {
  const user = shallowRef<UserProfile | null>(authSession.user)
  const authenticated = computed(() => user.value !== null)
  authSession.subscribe((profile, reason) => {
    user.value = profile
    if (reason === 'logout' || reason === 'expired' || reason === 'login') queryClient.clear()
  })
  return {
    user, authenticated,
    login: (credentials: LoginRequest) => authSession.login(credentials),
    logout: () => authSession.logout(),
    verify: () => authSession.verify(),
  }
})
