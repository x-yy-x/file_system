<template>
  <div class="top-bar">
    <div class="title">📁 文件系统管理模拟器</div>
    <div class="actions">
      <el-button size="small" @click="store.save()" plain style="color:white;border-color:rgba(255,255,255,0.3)">
        💾 保存
      </el-button>
      <el-button size="small" @click="store.getInfo()" plain style="color:white;border-color:rgba(255,255,255,0.3)">
        ℹ️ 信息
      </el-button>
      <el-button size="small" @click="store.format()" plain style="color:#f56c6c;border-color:#f56c6c">
        ⚠️ 格式化
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useFsStore } from '../../store/fileSystem.js'
const store = useFsStore()
</script>
