<script setup lang="ts">
export type AdminTableColumn = { key: string; label: string };

const props = withDefaults(defineProps<{
  columns: AdminTableColumn[];
  visibleColumns?: string[];
  modelValue?: string;
  sortKey?: string;
  sortDirection?: "asc" | "desc";
  selectedCount?: number;
  totalCount?: number;
  allSelected?: boolean;
}>(), {
  visibleColumns: undefined,
  modelValue: "",
  sortKey: "",
  sortDirection: "asc",
  selectedCount: 0,
  totalCount: 0,
  allSelected: false,
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
  "update:sortKey": [value: string];
  "update:sortDirection": [value: "asc" | "desc"];
  "update:visibleColumns": [value: string[]];
  "toggle-all": [];
  clearSelection: [];
}>();

function isVisible(column: AdminTableColumn) {
  return !props.visibleColumns || props.visibleColumns.includes(column.key);
}

function toggleColumn(key: string) {
  const current = props.visibleColumns ? [...props.visibleColumns] : props.columns.map((column) => column.key);
  if (current.includes(key)) {
    if (current.length === 1) return;
    emit("update:visibleColumns", current.filter((column) => column !== key));
  } else {
    emit("update:visibleColumns", [...current, key]);
  }
}

function cycleSort() {
  if (!props.sortKey) {
    emit("update:sortKey", props.columns[0]?.key || "");
    emit("update:sortDirection", "asc");
    return;
  }
  const index = props.columns.findIndex((column) => column.key === props.sortKey);
  if (props.sortDirection === "asc") {
    emit("update:sortDirection", "desc");
  } else {
    emit("update:sortKey", props.columns[(index + 1) % Math.max(props.columns.length, 1)]?.key || "");
    emit("update:sortDirection", "asc");
  }
}
</script>

<template>
  <div class="admin-table-controls" role="toolbar" aria-label="表格工具">
    <label class="admin-table-controls__search">
      <span class="admin-table-controls__search-icon" aria-hidden="true">⌕</span>
      <span class="sr-only">表格内筛选</span>
      <input :value="modelValue" type="search" placeholder="筛选当前页" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)" />
    </label>
    <button class="admin-table-controls__button" type="button" aria-label="排序" @click="cycleSort">
      <span aria-hidden="true">↕</span>排序{{ sortKey ? ` · ${columns.find((column) => column.key === sortKey)?.label || sortKey} ${sortDirection === 'asc' ? '升序' : '降序'}` : "" }}
    </button>
    <details class="admin-table-controls__columns">
      <summary aria-label="列显示"><span aria-hidden="true">☷</span>列显示</summary>
      <div class="admin-table-controls__menu">
        <label v-for="column in columns" :key="column.key" class="admin-table-controls__check">
          <input type="checkbox" :checked="isVisible(column)" @change="toggleColumn(column.key)" />
          <span>{{ column.label }}</span>
        </label>
      </div>
    </details>
    <label class="admin-table-controls__select-all">
      <input type="checkbox" :checked="allSelected" :disabled="!totalCount" aria-label="选择当前页" @change="emit('toggle-all')" />
      <span>选择当前页</span>
    </label>
    <span class="admin-table-controls__count">已选 {{ selectedCount }} / {{ totalCount }}</span>
    <button v-if="selectedCount" class="admin-table-controls__clear" type="button" @click="emit('clearSelection')">清除选择</button>
  </div>
</template>
