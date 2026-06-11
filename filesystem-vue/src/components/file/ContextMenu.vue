<template>
  <Teleport to="body">
    <div v-if="visible" class="context-menu-backdrop" @click.self="close" @contextmenu.prevent="close">
      <div class="context-menu" :style="{ left: x + 'px', top: y + 'px' }">
        <div v-for="item in items" :key="item.label"
             class="context-menu-item"
             :class="{ disabled: item.disabled, separator: item.separator }"
             @click.stop="exec(item)">
          <span class="cm-icon">{{ item.icon }}</span>
          <span class="cm-label">{{ item.label }}</span>
          <span class="cm-shortcut" v-if="item.shortcut">{{ item.shortcut }}</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'

const visible = ref(false)
const x = ref(0)
const y = ref(0)
const items = ref([])
let resolveCallback = null

function show(ev, menuItems) {
  x.value = ev.clientX
  y.value = ev.clientY
  items.value = menuItems
  visible.value = true

  // 防止菜单超出视口
  requestAnimationFrame(() => {
    const menu = document.querySelector('.context-menu')
    if (!menu) return
    const rect = menu.getBoundingClientRect()
    if (rect.right > window.innerWidth) x.value = window.innerWidth - rect.width - 8
    if (rect.bottom > window.innerHeight) y.value = window.innerHeight - rect.height - 8
  })

  return new Promise(resolve => {
    resolveCallback = resolve
  })
}

function exec(item) {
  if (item.disabled) return
  visible.value = false
  if (resolveCallback) {
    resolveCallback(item.action)
    resolveCallback = null
  }
  if (item.handler) item.handler()
}

function close() {
  visible.value = false
  if (resolveCallback) {
    resolveCallback(null)
    resolveCallback = null
  }
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))

defineExpose({ show })
</script>

<style scoped>
.context-menu-backdrop {
  position: fixed; inset: 0; z-index: 9999;
  background: transparent;
}
.context-menu {
  position: fixed; z-index: 10000;
  min-width: 180px; padding: 4px 0;
  background: #fff; border: 1px solid #d0d0d0;
  border-radius: 6px; box-shadow: 0 4px 20px rgba(0,0,0,0.15);
}
.context-menu-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 14px; cursor: pointer; font-size: 13px;
  transition: background 0.1s;
  user-select: none;
}
.context-menu-item:hover { background: #e8f0fe; }
.context-menu-item.disabled { color: #bbb; cursor: default; }
.context-menu-item.disabled:hover { background: transparent; }
.context-menu-item.separator { border-bottom: 1px solid #e8e8e8; margin: 4px 0; pointer-events: none; }
.cm-icon { width: 18px; text-align: center; font-size: 14px; }
.cm-label { flex: 1; }
.cm-shortcut { color: #999; font-size: 11px; }
</style>
