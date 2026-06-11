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
             ref="inputRef" placeholder="输入 help 查看所有命令及用法" />
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
        showHelp()
        break
      case 'format': await store.format(); break
      case 'save': await store.save(); break
      case 'info': await store.getInfo(); break
      case 'pwd': store.addConsole(store.currentPath, 'info'); break
      case 'ls': {
        store.addConsole(
          store.entries.map(e => (e.type === 'directory' ? '📁' : '📄') + ' ' + e.name).join('\n') || '(空)',
          'info'
        )
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
          else store.addConsole('删除失败（非空或不存在）', 'error')
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
            store.addConsole(
              `── ${s.name} ──\n类型: ${s.type}\n大小: ${s.size} B\nInode: ${s.inodeNumber}\n创建: ${new Date(s.createTime * 1000).toLocaleString()}\n修改: ${new Date(s.modifyTime * 1000).toLocaleString()}\n块数: ${s.blockCount}`,
              'info'
            )
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
          await store.preview(args[0])
        } else store.addConsole('用法: open <路径>', 'warning')
        break
      case 'close':
        store.previewFile = null
        store.addConsole('文件已关闭', 'success')
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

function showHelp() {
  const help = `
╔══════════════════════════════════════════════════════╗
║                     命令帮助                         ║
╠══════════════════════════════════════════════════════╣
║  系统管理                                           ║
║    format         格式化虚拟磁盘（所有数据丢失）       ║
║    save           手动保存磁盘映像到文件              ║
║    info           显示系统信息（块数/使用率等）       ║
║    exit / quit    保存并退出                         ║
║                                                     ║
║  目录操作                                           ║
║    mkdir <路径>   创建目录                           ║
║    rmdir <路径>   删除空目录                         ║
║    ls [路径]      列出目录内容                      ║
║    cd <路径>      切换工作目录                      ║
║    pwd            显示当前工作目录路径               ║
║    tree [路径]    树形显示目录结构                  ║
║                                                     ║
║  文件操作                                           ║
║    create <路径>  创建文件                          ║
║    open <路径>    打开文件（在预览面板中查看）       ║
║    close          关闭当前预览的文件                 ║
║    delete <路径>  删除文件                          ║
║                                                     ║
║  附加操作                                           ║
║    rename <旧> <新>  重命名文件或目录               ║
║    copy <源> <目标>  复制文件                      ║
║    move <源> <目标>  移动文件                      ║
║    stat <路径>      查看文件/目录详细信息           ║
║                                                     ║
║  💡 提示：也可以在文件列表中左键进入/打开，右键菜单操作 ║
╚══════════════════════════════════════════════════════╝
`
  store.addConsole(help, 'system')
}

</script>
