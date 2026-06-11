<template>
  <div class="tree-panel">
    <div class="panel-header">📂 目录树</div>
    <div class="tree-content">
      <TreeNode :node="store.treeData" :depth="0" />
    </div>
  </div>
</template>

<script setup>
import { useFsStore } from '../../store/fileSystem.js'
import TreeNode from './TreeNode.vue'
const store = useFsStore()
</script>
