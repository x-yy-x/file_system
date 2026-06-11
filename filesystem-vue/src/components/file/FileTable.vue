<template>
  <div class="file-list" @contextmenu.prevent="onTableContextMenu">
    <el-table :data="store.entries" stripe size="small"
              @row-click="onRowClick" highlight-current-row
              empty-text="（空目录）" ref="tableRef">
      <el-table-column label="名称" min-width="240">
        <template #default="{ row }">
          <span style="cursor:pointer">
            {{ row.type === 'directory' ? '📁 ' : '📄 ' }}{{ row.name }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ row.type === 'directory' ? '目录' : '文件' }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ row.type === 'directory' ? '-' : formatSize(row.size) }}</template>
      </el-table-column>
      <el-table-column label="修改时间" width="170">
        <template #default="{ row }">{{ formatTime(row.modifyTime) }}</template>
      </el-table-column>
    </el-table>

    <ContextMenu ref="ctxMenu" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useFsStore } from '../../store/fileSystem.js'
import ContextMenu from './ContextMenu.vue'

const store = useFsStore()
const ctxMenu = ref(null)
const tableRef = ref(null)
let menuTargetRow = null

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

/** 左键单击：目录进入，文件打开 */
function onRowClick(row) {
  const path = resolvePath(row.name)
  if (row.type === 'directory') {
    store.cd(path)
  } else {
    store.preview(path)
  }
}

/** 右键菜单 */
async function onTableContextMenu(ev) {
  // 检查是否点到了某一行
  const el = document.elementFromPoint(ev.clientX, ev.clientY)
  const rowEl = el?.closest('.el-table__row')
  if (!rowEl) {
    // 空白区域右键：显示全局菜单
    const action = await ctxMenu.value.show(ev, [
      { icon: '📁', label: '新建目录', action: 'mkdir' },
      { icon: '📄', label: '新建文件', action: 'create' },
      { icon: '-', label: '', disabled: true, separator: true },
      { icon: '🔄', label: '刷新', action: 'refresh' },
      { icon: '💾', label: '保存', action: 'save' },
      { icon: 'ℹ️', label: '系统信息', action: 'info' },
    ])
    handleGlobalAction(action)
    return
  }

  // 获取对应行数据
  const idx = Array.from(rowEl.parentElement.children).indexOf(rowEl)
  menuTargetRow = store.entries[idx]
  if (!menuTargetRow) return

  const isDir = menuTargetRow.type === 'directory'
  const path = resolvePath(menuTargetRow.name)

  const items = [
    { icon: isDir ? '📂' : '📄', label: isDir ? '进入' : '打开', action: 'open' },
    { icon: '✏️', label: '重命名', action: 'rename' },
    { icon: '-', label: '', disabled: true, separator: true },
    { icon: '📋', label: '复制', action: 'copy' },
    { icon: '✂️', label: '移动', action: 'move' },
    { icon: '-', label: '', disabled: true, separator: true },
    { icon: 'ℹ️', label: '属性', action: 'stat' },
    { icon: '', label: '', disabled: true, separator: true },
    { icon: '🗑️', label: '删除', action: 'delete', shortcut: 'Del' },
  ]

  const action = await ctxMenu.value.show(ev, items)
  handleRowAction(action, path, menuTargetRow)
}

function handleRowAction(action, path, row) {
  switch (action) {
    case 'open':
      if (row.type === 'directory') store.cd(path)
      else store.preview(path)
      break
    case 'rename': {
      const newName = prompt('新名称:', row.name)
      if (newName && newName !== row.name) {
        const newPath = resolvePath(newName)
        store.rename(path, newPath)
      }
      break
    }
    case 'copy': {
      const dst = prompt('目标路径:', path + '.copy')
      if (dst) store.copyFile(path, dst)
      break
    }
    case 'move': {
      const dst = prompt('目标路径:')
      if (dst) store.moveFile(path, dst)
      break
    }
    case 'stat': showStat(path); break
    case 'delete': store.deleteFile(path); break
  }
}

function handleGlobalAction(action) {
  switch (action) {
    case 'mkdir': {
      const name = prompt('目录名:')
      if (name) {
        const path = store.currentPath === '/' ? '/' + name : store.currentPath + '/' + name
        store.mkdir(path)
      }
      break
    }
    case 'create': {
      const name = prompt('文件名:')
      if (name) {
        const path = store.currentPath === '/' ? '/' + name : store.currentPath + '/' + name
        store.createFile(path)
      }
      break
    }
    case 'refresh': store.refresh(); break
    case 'save': store.save(); break
    case 'info': store.getInfo(); break
  }
}

async function showStat(path) {
  const res = await (await import('../../api/fileSystem.js')).default.stat(path)
  if (res.success) {
    const s = res.data
    store.addConsole(
      `── ${s.name} ──\n类型: ${s.type}\n大小: ${s.size} B\nInode: ${s.inodeNumber}\n创建: ${new Date(s.createTime * 1000).toLocaleString()}\n修改: ${new Date(s.modifyTime * 1000).toLocaleString()}\n块数: ${s.blockCount}`,
      'info'
    )
  }
}

function resolvePath(name) {
  return store.currentPath === '/' ? '/' + name : store.currentPath + '/' + name
}
</script>
