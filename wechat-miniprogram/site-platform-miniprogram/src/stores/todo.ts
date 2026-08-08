import { reactive } from 'vue';
import { getScopedTodoPage, getTodoSummary } from '@/api/todo';
import type { PageResult, TodoItem, TodoSummary } from '@/types';

export function todoItemIdentity(item: TodoItem) {
  if (item.todoKey) return item.todoKey;
  return [item.scope || 'PENDING', item.businessType || '', item.taskType || item.type,
    item.taskId || '', item.targetId || item.id].join(':');
}

export function mergeTodoItems(current: TodoItem[], incoming: TodoItem[]) {
  const merged = new Map<string, TodoItem>();
  current.forEach((item) => merged.set(todoItemIdentity(item), item));
  incoming.forEach((item) => merged.set(todoItemIdentity(item), item));
  return [...merged.values()];
}

const state = reactive<{
  todos: TodoItem[];
  todoPage: Omit<PageResult<TodoItem>, 'records'>;
  summary: TodoSummary;
}>({
  todos: [],
  todoPage: { pageNo: 0, pageSize: 20, total: 0 },
  summary: { pendingCount: 0, ccCount: 0, unreadNotificationCount: 0, badgeCount: 0 }
});

let todoRequestSequence = 0;

export function useTodoStore() {
  async function loadTodos(projectId?: number) {
    const requestSequence = ++todoRequestSequence;
    try {
      const result = await getScopedTodoPage('PENDING', { projectId, pageNo: 1, pageSize: 20 });
      if (requestSequence !== todoRequestSequence) return result;
      state.todos = mergeTodoItems([], result.records || []);
      state.todoPage = { pageNo: result.pageNo, pageSize: result.pageSize, total: result.total };
      return result;
    } catch (error) {
      if (requestSequence === todoRequestSequence) {
        state.todos = [];
        state.todoPage = { pageNo: 0, pageSize: 20, total: 0 };
      }
      throw error;
    }
  }

  async function loadSummary() {
    try {
      state.summary = await getTodoSummary();
    } catch {
      state.summary = {
        pendingCount: state.todos.length,
        ccCount: 0,
        unreadNotificationCount: 0,
        badgeCount: state.todos.length
      };
    }
  }

  function markDone(id: number) {
    const previousLength = state.todos.length;
    state.todos = state.todos.filter((item) => item.id !== id);
    if (state.todos.length !== previousLength) {
      state.todoPage.total = Math.max(0, state.todoPage.total - 1);
    }
  }

  return {
    state,
    loadTodos,
    loadSummary,
    markDone
  };
}
