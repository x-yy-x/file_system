import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 5000,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.response.use(
  res => res.data,
  err => {
    const msg = err.response?.data?.message || err.message || '网络错误'
    return { success: false, message: msg }
  }
)

export default {
  // 系统
  format()                  { return api.post('/format') },
  save()                    { return api.post('/save') },
  getInfo()                 { return api.get('/info') },

  // 目录
  mkdir(path)               { return api.post('/mkdir', { path }) },
  rmdir(path)               { return api.post('/rmdir', { path }) },
  ls(path = '.')            { return api.get('/ls', { params: { path } }) },
  cd(path)                  { return api.post('/cd', { path }) },
  pwd()                     { return api.get('/pwd') },
  tree(path = '.')          { return api.get('/tree', { params: { path } }) },

  // 文件
  create(path)              { return api.post('/create', { path }) },
  open(path, mode = 2)      { return api.post('/open', { path, mode }) },
  close(fd)                 { return api.post('/close', { fd }) },
  read(fd, size = 4096)     { return api.post('/read', { fd, size }) },
  write(fd, data)           { return api.post('/write', { fd, data }) },
  delete(path)              { return api.post('/delete', { path }) },

  // 附加
  rename(oldPath, newPath)  { return api.post('/rename', { oldPath, newPath }) },
  copy(srcPath, dstPath)    { return api.post('/copy', { srcPath, dstPath }) },
  move(srcPath, dstPath)    { return api.post('/move', { srcPath, dstPath }) },
  stat(path)                { return api.get('/stat', { params: { path } }) },

  // 文件内容
  getFileContent(path)      { return api.get('/file/content', { params: { path } }) },
  setFileContent(path, content) { return api.post('/file/content', { path, content }) }
}
