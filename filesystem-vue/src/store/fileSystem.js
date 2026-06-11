import { defineStore } from 'pinia'
import api from '../api/fileSystem.js'

export const useFsStore = defineStore('fileSystem', {
  state: () => ({
    currentPath: '/',
    entries: [],
    systemInfo: null,
    previewFile: null,
    previewContent: '',
    selectedEntry: null,
    treeData: { name: '/', type: 'directory', children: [], inode: 0 },
    loading: false,
    consoleLines: [
      { text: '文件系统管理模拟器 v1.0', type: 'system' },
      { text: '输入 help 查看命令帮助', type: 'system' },
      { text: '提示: 在下方控制台输入命令或通过界面上方操作', type: 'system' }
    ],
    openFiles: []
  }),

  actions: {
    addConsole(text, type = 'info') {
      this.consoleLines.push({ text, type })
    },

    async refreshDir(path) {
      this.loading = true
      const p = path || this.currentPath
      const res = await api.ls(p)
      if (res.success) {
        this.entries = res.data || []
        this.currentPath = p
      } else {
        this.addConsole('刷新目录失败: ' + res.message, 'error')
      }
      this.loading = false
    },

    async cd(path) {
      const res = await api.cd(path)
      if (res.success) {
        const pwdRes = await api.pwd()
        if (pwdRes.success) {
          this.currentPath = pwdRes.data.path
        }
        await this.refreshDir(this.currentPath)
        await this.loadTree()
        return true
      }
      this.addConsole('目录切换失败: ' + (res.message || path), 'error')
      return false
    },

    async refresh() {
      await this.refreshDir(this.currentPath)
    },

    async loadTree() {
      const res = await api.tree('/')
      if (res.success && res.data?.tree) {
        this.parseTree(res.data.tree)
      }
    },

    parseTree(treeStr) {
      const lines = treeStr.split('\n').filter(l => l.trim())
      if (lines.length === 0) return
      const root = { name: '/', type: 'directory', children: [], expanded: true }
      const stack = [{ node: root, indent: -1 }]

      for (let i = 1; i < lines.length; i++) {
        const line = lines[i]
        const indent = line.search(/[^│ ]/)
        const clean = line.replace(/[├└│]/g, '').replace(/──/, '').trim()
        if (!clean) continue

        const isDir = clean.endsWith('/')
        const name = isDir ? clean.slice(0, -1) : clean.replace(/ \(.*\)$/, '')
        const isLast = line.includes('└──')

        while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
          stack.pop()
        }

        const parent = stack[stack.length - 1].node
        const node = { name, type: isDir ? 'directory' : 'file', children: [], expanded: false }
        parent.children.push(node)
        if (isDir) stack.push({ node, indent })
      }
      this.treeData = root
    },

    async format() {
      if (!confirm('确认格式化？所有数据将丢失！')) return
      const res = await api.format()
      if (res.success) {
        this.addConsole('格式化完成', 'success')
        await this.cd('/')
      } else {
        this.addConsole('格式化失败: ' + res.message, 'error')
      }
    },

    async mkdir(path) {
      const res = await api.mkdir(path)
      if (res.success) {
        this.addConsole('目录已创建: ' + path, 'success')
        await this.refresh()
        await this.loadTree()
      } else {
        this.addConsole('创建目录失败', 'error')
      }
    },

    async createFile(path) {
      const res = await api.create(path)
      if (res.success) {
        this.addConsole('文件已创建: ' + path, 'success')
        await this.refresh()
      } else {
        this.addConsole('创建文件失败', 'error')
      }
    },

    async deleteFile(path) {
      if (!confirm('确认删除 "' + path + '" ？')) return
      const res = await api.delete(path)
      if (res.success) {
        this.addConsole('已删除: ' + path, 'success')
        if (this.previewFile === path) this.previewFile = null
        await this.refresh()
        await this.loadTree()
      } else {
        this.addConsole('删除失败', 'error')
      }
    },

    async rename(oldPath, newPath) {
      const res = await api.rename(oldPath, newPath)
      if (res.success) {
        this.addConsole('重命名成功', 'success')
        await this.refresh()
        await this.loadTree()
      } else {
        this.addConsole('重命名失败', 'error')
      }
    },

    async preview(path) {
      const res = await api.getFileContent(path)
      if (res.success) {
        this.previewFile = path
        this.previewContent = res.data?.content || ''
      } else {
        this.addConsole('打开文件失败', 'error')
      }
    },

    async saveFile(path, content) {
      const res = await api.setFileContent(path, content)
      if (res.success) {
        this.addConsole('文件已保存: ' + path, 'success')
        this.previewContent = content
      } else {
        this.addConsole('保存失败', 'error')
      }
    },

    async copyFile(src, dst) {
      const res = await api.copy(src, dst)
      if (res.success) {
        this.addConsole('复制成功: ' + src + ' → ' + dst, 'success')
        await this.refresh()
      } else {
        this.addConsole('复制失败', 'error')
      }
    },

    async moveFile(src, dst) {
      const res = await api.move(src, dst)
      if (res.success) {
        this.addConsole('移动成功: ' + src + ' → ' + dst, 'success')
        await this.refresh()
        await this.loadTree()
      } else {
        this.addConsole('移动失败', 'error')
      }
    },

    async getInfo() {
      const res = await api.getInfo()
      if (res.success) {
        this.systemInfo = res.data
        let msg = `总块数: ${res.data.totalBlocks} | 空闲块: ${res.data.freeBlocks} | `
        msg += `inode: ${res.data.freeInodes}/${res.data.totalInodes} | 使用率: ${res.data.usageRate.toFixed(2)}%`
        this.addConsole(msg, 'info')
      }
    },

    async save() {
      const res = await api.save()
      if (res.success) {
        this.addConsole('保存完成', 'success')
      }
    }
  }
})
