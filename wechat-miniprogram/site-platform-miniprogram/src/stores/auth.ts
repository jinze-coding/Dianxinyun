import { reactive } from 'vue';
import type { User } from '@/types';
import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth';
import { getToken } from '@/api/request';
import { USE_MOCK } from '@/api/request';

const state = reactive<{
  user: User | null;
  tokenReady: boolean;
}>({
  user: null,
  tokenReady: USE_MOCK || Boolean(getToken())
});

export function useAuthStore() {
  async function login(username: string, password: string) {
    state.user = await loginApi({ username, password });
    state.tokenReady = true;
  }

  async function loadUser() {
    if (!state.tokenReady) {
      return null;
    }
    state.user = await getCurrentUser();
    return state.user;
  }

  async function logout() {
    await logoutApi();
    state.user = null;
    state.tokenReady = false;
  }

  return {
    state,
    login,
    loadUser,
    logout
  };
}
