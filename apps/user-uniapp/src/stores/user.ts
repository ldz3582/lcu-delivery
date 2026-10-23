import { defineStore } from 'pinia'
import { api } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({ profile: null as any }),
  actions: {
    async load() {
      this.profile = await api.profile()
      return this.profile
    }
  }
})
