import { reactive } from 'vue';
import { getTodoItems } from '@/api/todo';
import type { TodoItem } from '@/types';

const state = reactive<{
  todos: TodoItem[];
}>({
  todos: []
});

export function useTodoStore() {
  async function loadTodos() {
    try {
      state.todos = await getTodoItems();
    } catch (error) {
      state.todos = [];
      throw error;
    }
  }

  function markDone(id: number) {
    state.todos = state.todos.filter((item) => item.id !== id);
  }

  return {
    state,
    loadTodos,
    markDone
  };
}
