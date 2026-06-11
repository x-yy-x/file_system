<template>
  <div class="nav-bar">
    <div class="breadcrumb">
      <span v-for="(seg, i) in segments" :key="i"
            style="cursor:pointer;color:#409eff;font-size:13px"
            @click="navigateTo(i)">
        <span v-if="i > 0" style="color:#999;margin:0 2px">/</span>
        {{ seg }}
      </span>
    </div>
    <div class="btn-group">
      <el-button size="small" type="primary" @click="showNewFile = true" title="在当前目录创建新文件">📄 新建文件</el-button>
      <el-button size="small" type="success" @click="showNewDir = true" title="在当前目录创建子目录">📁 新建目录</el-button>
      <el-button size="small" @click="showStatDialog" title="查看当前目录详细信息">ℹ️ 属性</el-button>
      <el-button size="small" @click="store.refresh()" title="刷新当前目录列表">🔄 刷新</el-button>
    </div>

    <el-dialog v-model="showNewFile" title="新建文件" width="400px">
      <el-input v-model="newFileName" placeholder="输入文件名" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showNewFile = false">取消</el-button>
          <el-button type="primary" @click="doNewFile">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="showNewDir" title="新建目录" width="400px">
      <el-input v-model="newDirName" placeholder="输入目录名" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showNewDir = false">取消</el-button>
          <el-button type="primary" @click="doNewDir">创建</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFsStore } from '../../store/fileSystem.js'

const store = useFsStore()
const showNewFile = ref(false)
const showNewDir = ref(false)
const newFileName = ref('')
const newDirName = ref('')

const segments = computed(() => {
  const path = store.currentPath || '/'
  if (path === '/') return ['/']
  return ['/', ...path.split('/').filter(Boolean)]
})

function navigateTo(index) {
  if (index === 0) { store.cd('/'); return }
  const parts = segments.value.slice(1, index + 1)
  store.cd('/' + parts.join('/'))
}

async function doNewFile() {
  if (!newFileName.value) return
  const path = store.currentPath === '/' ? '/' + newFileName.value : store.currentPath + '/' + newFileName.value
  await store.createFile(path)
  showNewFile.value = false
  newFileName.value = ''
}

async function doNewDir() {
  if (!newDirName.value) return
  const path = store.currentPath === '/' ? '/' + newDirName.value : store.currentPath + '/' + newDirName.value
  await store.mkdir(path)
  showNewDir.value = false
  newDirName.value = ''
}

async function showStatDialog() {
  const path = store.currentPath
  const res = await (await import('../../api/fileSystem.js')).default.stat(path)
  if (res.success) {
    const s = res.data
    store.addConsole(
      `── ${s.name || '/'} ──\n类型: ${s.type}\n大小: ${s.size} B\nInode: ${s.inodeNumber}\n创建: ${new Date(s.createTime * 1000).toLocaleString()}\n修改: ${new Date(s.modifyTime * 1000).toLocaleString()}\n块数: ${s.blockCount}`,
      'info'
    )
  } else {
    store.addConsole(`属性: ${path} (根目录)`, 'info')
  }
}
</script>
