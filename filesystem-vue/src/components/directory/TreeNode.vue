<template>
  <div class="tree-node" :style="{ paddingLeft: depth * 10 + 'px' }">
    <div class="node-label"
         :class="{ active: isActive }"
         @contextmenu.prevent="onContextMenu">
      <!-- 展开/折叠三角（单独处理，不触发导航） -->
      <span v-if="isDir" class="expand-icon" @click.stop="toggleExpand">{{ node.expanded ? '▼' : '▶' }}</span>
      <span v-else class="expand-icon"></span>
      <!-- 图标 + 名称（左键导航/打开） -->
      <span @click="onNavigate">{{ isDir ? '📁' : '📄' }}</span>
      <span @click="onNavigate" style="flex:1">{{ node.name === '/' ? '/' : node.name }}</span>
    </div>
    <div v-if="node.expanded && node.children" class="node-children">
      <TreeNode v-for="(child, i) in node.children" :key="child.name + '-' + i"
                :node="child" :depth="depth + 1" />
    </div>

    <ContextMenu ref="ctxMenu" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFsStore } from '../../store/fileSystem.js'
import ContextMenu from '../file/ContextMenu.vue'

const props = defineProps({
  node: Object,
  depth: { type: Number, default: 0 }
})

const store = useFsStore()
const ctxMenu = ref(null)

const isDir = computed(() => props.node.type === 'directory')

const isActive = computed(() => {
  if (!isDir.value) return false
  const currentPath = store.currentPath === '/' ? '/' : store.currentPath
  const nodePath = props.node._path
  return nodePath === currentPath
})

/** 点击展开三角：仅切换展开/折叠，不导航 */
function toggleExpand() {
  props.node.expanded = !props.node.expanded
  if (props.node.expanded && props.node.name === '/' && !props.node.children.length) {
    store.loadTree()
  }
}

/** 点击图标或名称：目录导航进入，文件打开预览 */
function onNavigate() {
  if (isDir.value) {
    if (props.node.name === '/') {
      store.cd('/')
      return
    }
    const path = findNodePath(props.node)
    if (path) store.cd(path)
  } else {
    const path = findNodePath(props.node)
    if (path) store.preview(path)
  }
}

/** 右键菜单 */
async function onContextMenu(ev) {
  const path = findNodePath(props.node)

  if (isDir.value) {
    const action = await ctxMenu.value.show(ev, [
      { icon: '📂', label: '进入', action: 'cd' },
      { icon: '✏️', label: '重命名', action: 'rename' },
      { icon: '-', label: '', disabled: true, separator: true },
      { icon: '📁', label: '新建子目录', action: 'mkdir' },
      { icon: '📄', label: '新建文件', action: 'create' },
      { icon: '-', label: '', disabled: true, separator: true },
      { icon: 'ℹ️', label: '属性', action: 'stat' },
      { icon: '🗑️', label: '删除目录', action: 'delete' },
    ])
    handleDirAction(action, path)
  } else {
    const action = await ctxMenu.value.show(ev, [
      { icon: '📄', label: '打开', action: 'open', shortcut: '单击' },
      { icon: '✏️', label: '重命名', action: 'rename' },
      { icon: '📋', label: '复制', action: 'copy' },
      { icon: '✂️', label: '移动', action: 'move' },
      { icon: '-', label: '', disabled: true, separator: true },
      { icon: 'ℹ️', label: '属性', action: 'stat' },
      { icon: '🗑️', label: '删除', action: 'delete' },
    ])
    handleFileAction(action, path)
  }
}

function handleDirAction(action, path) {
  switch (action) {
    case 'cd': store.cd(path); break
    case 'rename': {
      const newName = prompt('新目录名:', props.node.name)
      if (newName && newName !== props.node.name) {
        const parentPath = path.substring(0, path.lastIndexOf('/')) || '/'
        const newPath = parentPath === '/' ? '/' + newName : parentPath + '/' + newName
        store.rename(path, newPath)
      }
      break
    }
    case 'mkdir': {
      const name = prompt('子目录名:')
      if (name) {
        const newPath = path === '/' ? '/' + name : path + '/' + name
        store.mkdir(newPath)
      }
      break
    }
    case 'create': {
      const name = prompt('文件名:')
      if (name) {
        const newPath = path === '/' ? '/' + name : path + '/' + name
        store.createFile(newPath)
      }
      break
    }
    case 'stat': showStat(path); break
    case 'delete': store.deleteFile(path); break
  }
}

function handleFileAction(action, path) {
  switch (action) {
    case 'open': store.preview(path); break
    case 'rename': {
      const newName = prompt('新文件名:', props.node.name)
      if (newName && newName !== props.node.name) {
        const parentPath = path.substring(0, path.lastIndexOf('/')) || '/'
        const newPath = parentPath === '/' ? '/' + newName : parentPath + '/' + newName
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

/** 递归查找节点在文件系统中的完整路径 */
function findNodePath(targetNode) {
  if (targetNode.name === '/') return '/'
  // 从根开始搜索
  const result = findInTree(store.treeData, targetNode, [])
  return result ? '/' + result.join('/') : '/' + targetNode.name
}

function findInTree(node, target, path) {
  if (node === target) return path

  if (node.children) {
    for (const child of node.children) {
      if (child === target) {
        path.push(child.name)
        return path
      }
      const newPath = [...path, child.name]
      const found = findInTree(child, target, newPath)
      if (found) return found
    }
  }
  return null
}
</script>
