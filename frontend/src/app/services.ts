import { QueryClient } from '@tanstack/vue-query'
import { AuthSession } from '../features/auth/session'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, staleTime: 30_000 },
    mutations: { retry: false },
  },
})
export const authSession = new AuthSession()
