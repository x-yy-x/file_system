<template>
  <div class="file-list">
    <el-table :data="store.entries" stripe size="small" @row-dblclick="handleDoubleClick"
              @row-click="handleClick" highlight-current-row empty-text="（空目录）">
      <el-table-column label="名称" min-width="200">
        <template #default="{ row }">
          <span>{{ row.type === 'directory' ? '📁 ' : '📄 ' }}{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ row.type === 'directory' ? '目录' : '文件' }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ row.type === 'directory' ? '-' : formatSize(row.size) }}</template>
      </el-table-column>
      <el-table-column label="修改时间" width="160">
        <template #default="{ row }">{{ formatTime(row.modifyTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click.stop="previewEntry(row)">打开</el-button>
          <el-button size="small" text type="warning" @click.stop="renameEntry(row)">重命名</el-button>
          <el-button size="small" text type="danger" @click.stop="deleteEntry(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { useFsStore } from '../../store/fileSystem.js'

const store = useFsStore()

function formatSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts * 1000)
  return d.toLocaleString('zh-CN', { hour12: false })
}

function handleDoubleClick(row) {
  if (row.type === 'directory') {
    const path = store.currentPath === '/' ? '/' + row.name : store.currentPath + '/' + row.name
    store.cd(path)
  } else {
    const path = store.currentPath === '/' ? '/' + row.name : store.currentPath + '/' + row.name
    store.preview(path)
  }
}

function handleClick(row) {
  store.selectedEntry = row
}

function previewEntry(row) {
  const path = store.currentPath === '/' ? '/' + row.name : store.currentPath + '/' + row.name
  if (row.type === 'directory') {
    store.cd(path)
  } else {
    store.preview(path)
  }
}

function renameEntry(row) {
  const path = store.currentPath === '/' ? '/' + row.name : store.currentPath + '/' + row.name
  const newName = prompt('新名称:', row.name)
  if (newName && newName !== row.name) {
    const newPath = store.currentPath === '/' ? '/' + newName : store.currentPath + '/' + newName
    store.rename(path, newPath)
  }
}

function deleteEntry(row) {
  const path = store.currentPath === '/' ? '/' + row.name : store.currentPath + '/' + row.name
  store.deleteFile(path)
}
</script>
