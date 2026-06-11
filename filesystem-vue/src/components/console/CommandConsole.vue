<template>
  <div class="console-panel">
    <div class="console-header">
      <span>💻 命令控制台</span>
      <el-button size="small" text style="color:#888" @click="store.consoleLines = []">清屏</el-button>
    </div>
    <div class="console-output" ref="outputRef">
      <div v-for="(line, i) in store.consoleLines" :key="i"
           :style="{ color: lineColors[line.type] || '#d4d4d4' }">
        {{ line.text }}
      </div>
    </div>
    <div class="console-input-line">
      <span class="console-prompt">{{ store.currentPath }}$</span>
      <input class="console-input" v-model="command" @keydown.enter="execCommand"
             ref="inputRef" placeholder="输入命令 (help 查看帮助)" />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { useFsStore } from '../../store/fileSystem.js'

const store = useFsStore()
const command = ref('')
const outputRef = ref(null)
const inputRef = ref(null)

const lineColors = {
  system: '#569cd6',
  info: '#d4d4d4',
  success: '#4ec9b0',
  error: '#f44747',
  warning: '#dcdcaa'
}

watch(() => store.consoleLines.length, () => {
  nextTick(() => {
    if (outputRef.value) outputRef.value.scrollTop = outputRef.value.scrollHeight
  })
})

function execCommand() {
  const cmd = command.value.trim()
  if (!cmd) return
  store.addConsole('$ ' + cmd, 'info')
  command.value = ''
  parseCommand(cmd)
  nextTick(() => inputRef.value?.focus())
}

async function parseCommand(input) {
  const parts = input.split(/\s+/)
  const cmd = parts[0].toLowerCase()
  const args = parts.slice(1)

  try {
    switch (cmd) {
      case 'help':
      case '?':
        store.addConsole('命令列表: format, mkdir, rmdir, ls, cd, pwd, tree', 'system')
        store.addConsole('  create, open, close, read, write, delete', 'system')
        store.addConsole('  rename, copy, move, stat, save, info, exit', 'system')
        break
      case 'format': await store.format(); break
      case 'save': await store.save(); break
      case 'info': await store.getInfo(); break
      case 'pwd': store.addConsole(store.currentPath, 'info'); break
      case 'ls': {
        const path = args[0] || '.'
        store.addConsole(store.entries.map(e => (e.type === 'directory' ? '📁' : '📄') + ' ' + e.name).join('\n') || '(空)', 'info')
        break
      }
      case 'cd':
        if (args[0]) await store.cd(args[0])
        else store.addConsole('用法: cd <路径>', 'warning')
        break
      case 'mkdir':
        if (args[0]) await store.mkdir(args[0])
        else store.addConsole('用法: mkdir <路径>', 'warning')
        break
      case 'rmdir': {
        if (args[0]) {
          const res = await (await import('../../api/fileSystem.js')).default.rmdir(args[0])
          if (res.success) { store.addConsole('目录已删除', 'success'); await store.refresh(); await store.loadTree() }
          else store.addConsole('删除失败', 'error')
        } else store.addConsole('用法: rmdir <路径>', 'warning')
        break
      }
      case 'tree': {
        const res = await (await import('../../api/fileSystem.js')).default.tree(args[0] || '.')
        if (res.success) store.addConsole(res.data?.tree || '', 'info')
        break
      }
      case 'create':
        if (args[0]) await store.createFile(args[0])
        else store.addConsole('用法: create <路径>', 'warning')
        break
      case 'stat': {
        if (args[0]) {
          const res = await (await import('../../api/fileSystem.js')).default.stat(args[0])
          if (res.success) {
            const s = res.data
            store.addConsole(`名称: ${s.name}\n类型: ${s.type}\n大小: ${s.size} B\nInode: ${s.inodeNumber}\n创建: ${new Date(s.createTime * 1000).toLocaleString()}\n修改: ${new Date(s.modifyTime * 1000).toLocaleString()}\n块数: ${s.blockCount}`, 'info')
          } else store.addConsole('stat 失败', 'error')
        } else store.addConsole('用法: stat <路径>', 'warning')
        break
      }
      case 'rename':
        if (args.length >= 2) await store.rename(args[0], args[1])
        else store.addConsole('用法: rename <旧路径> <新路径>', 'warning')
        break
      case 'copy':
        if (args.length >= 2) await store.copyFile(args[0], args[1])
        else store.addConsole('用法: copy <源路径> <目标路径>', 'warning')
        break
      case 'move':
        if (args.length >= 2) await store.moveFile(args[0], args[1])
        else store.addConsole('用法: move <源路径> <目标路径>', 'warning')
        break
      case 'delete':
        if (args[0]) await store.deleteFile(args[0])
        else store.addConsole('用法: delete <路径>', 'warning')
        break
      case 'open':
        if (args[0]) {
          const path = args[0]
          await store.preview(path)
        } else store.addConsole('用法: open <路径>', 'warning')
        break
      case 'exit':
      case 'quit':
        await store.save()
        store.addConsole('保存完成。再见！', 'system')
        break
      default:
        store.addConsole('未知命令: ' + cmd + '，输入 help 查看帮助', 'warning')
    }
  } catch (e) {
    store.addConsole('错误: ' + e.message, 'error')
  }
}
</script>
