<template>
  <div class="file-preview" v-if="store.previewFile">
    <div class="preview-header">
      <span>📄 {{ store.previewFile }}</span>
      <div>
        <el-button size="small" type="primary" @click="save">💾 保存</el-button>
        <el-button size="small" @click="close">✖ 关闭</el-button>
      </div>
    </div>
    <div class="preview-content">
      <textarea v-model="content" spellcheck="false"></textarea>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useFsStore } from '../../store/fileSystem.js'

const store = useFsStore()
const content = ref('')

watch(() => store.previewContent, (val) => {
  content.value = val || ''
})

function save() {
  if (store.previewFile) {
    store.saveFile(store.previewFile, content.value)
  }
}

function close() {
  store.previewFile = null
  store.previewContent = ''
}
</script>
