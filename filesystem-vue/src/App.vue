<template>
  <div class="app-container">
    <TopBar />
    <div class="main-area">
      <DirTreePanel />
      <div class="content-panel">
        <NavBar />
        <FileTable />
        <FilePreview />
      </div>
    </div>
    <CommandConsole />
  </div>
</template>

<script setup>
import TopBar from './components/layout/TopBar.vue'
import DirTreePanel from './components/directory/DirTreePanel.vue'
import NavBar from './components/layout/NavBar.vue'
import FileTable from './components/file/FileTable.vue'
import FilePreview from './components/file/FilePreview.vue'
import CommandConsole from './components/console/CommandConsole.vue'
</script>
