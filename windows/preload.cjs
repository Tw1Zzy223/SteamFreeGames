const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("steamHunter", {
  version: "0.4.0",
  windowAction: (action) => ipcRenderer.invoke("window-action", action),
  catalog: (options) => ipcRenderer.invoke("catalog", options),
  details: (appId) => ipcRenderer.invoke("details", appId),
  external: (url) => ipcRenderer.invoke("external", url),
  copy: (value) => ipcRenderer.invoke("copy", value),
  openSteam: (appId) => ipcRenderer.invoke("open-steam", appId),
  readStore: () => ipcRenderer.invoke("store-read"),
  writeStore: (patch) => ipcRenderer.invoke("store-write", patch),
  steamNews: (appId) => ipcRenderer.invoke("steam-news", appId),
  priceHistory: (appId, region) => ipcRenderer.invoke("price-history", appId, region),
  supportSend: (payload) => ipcRenderer.invoke("support-send", payload),
  supportMessages: (key) => ipcRenderer.invoke("support-messages", key),
  checkUpdate: () => ipcRenderer.invoke("check-update"),
  installUpdate: () => ipcRenderer.invoke("install-update"),
  desktopSettings: (settings) => ipcRenderer.invoke("desktop-settings", settings),
  onShortcutSearch: (callback) => ipcRenderer.on("shortcut-search", () => callback()),
  onShortcutRefresh: (callback) => ipcRenderer.on("shortcut-refresh", () => callback()),
  onUpdateStatus: (callback) => ipcRenderer.on("update-status", (_event, status) => callback(status)),
});
