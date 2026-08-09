const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("steamHunter", {
  version: "0.3.0",
  windowAction: (action) => ipcRenderer.invoke("window-action", action),
  catalog: (options) => ipcRenderer.invoke("catalog", options),
  details: (appId) => ipcRenderer.invoke("details", appId),
  external: (url) => ipcRenderer.invoke("external", url),
  copy: (value) => ipcRenderer.invoke("copy", value),
  openSteam: (appId) => ipcRenderer.invoke("open-steam", appId),
  readStore: () => ipcRenderer.invoke("store-read"),
  writeStore: (patch) => ipcRenderer.invoke("store-write", patch),
  supportSend: (payload) => ipcRenderer.invoke("support-send", payload),
  supportMessages: (key) => ipcRenderer.invoke("support-messages", key),
  checkUpdate: () => ipcRenderer.invoke("check-update"),
});
