<template>
  <div class="tree-node" :style="{ paddingLeft: depth * 16 + 'px' }">
    <div v-if="node.type === 'directory'" class="node-label"
         :class="{ active: isActive }" @click="toggle">
      <span class="expand-icon">{{ node.expanded ? '▼' : '▶' }}</span>
      <span>📁</span>
      <span>{{ node.name === '/' ? '/' : node.name }}</span>
    </div>
    <div v-else class="node-label" :class="{ active: isActive }" @click="openFile">
      <span class="expand-icon"></span>
      <span>📄</span>
      <span>{{ node.name }}</span>
    </div>
    <div v-if="node.expanded && node.children" class="node-children">
      <TreeNode v-for="child in node.children" :key="child.name + child.type"
                :node="child" :depth="depth + 1" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFsStore } from '../../store/fileSystem.js'

const props = defineProps({
  node: Object,
  depth: { type: Number, default: 0 }
})

const store = useFsStore()

const isActive = computed(() => {
  if (props.node.type === 'directory') {
    const path = store.currentPath === '/' ? '/' : store.currentPath
    return props.node.name === '/' ? path === '/' : path.endsWith('/' + props.node.name)
  }
  return false
})

function toggle() {
  props.node.expanded = !props.node.expanded
  if (props.node.expanded && props.node.name === '/' && !props.node.children.length) {
    store.loadTree()
  }
}

function openFile() {
  const parentPath = findParentPath(props.node)
  store.preview(parentPath + '/' + props.node.name)
}

function findParentPath(node) {
  return store.currentPath
}
</script>
