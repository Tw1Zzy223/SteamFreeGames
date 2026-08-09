import { index, integer, sqliteTable, text } from "drizzle-orm/sqlite-core";

export const supportMessages = sqliteTable("support_messages", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  deviceId: text("device_id").notNull(),
  name: text("name").notNull(),
  contact: text("contact").notNull().default(""),
  message: text("message").notNull(),
  status: text("status").notNull().default("new"),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull(),
}, (table) => [
  index("idx_support_created_at").on(table.createdAt),
  index("idx_support_device_created").on(table.deviceId, table.createdAt),
]);

export const steamAuthStates = sqliteTable("steam_auth_states", {
  nonce: text("nonce").primaryKey(),
  deviceId: text("device_id").notNull(),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull(),
}, (table) => [index("idx_steam_auth_created").on(table.createdAt)]);

export const steamSessions = sqliteTable("steam_sessions", {
  tokenHash: text("token_hash").primaryKey(),
  deviceId: text("device_id").notNull(),
  steamId: text("steam_id").notNull(),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull(),
  lastUsedAt: integer("last_used_at", { mode: "timestamp_ms" }).notNull(),
}, (table) => [
  index("idx_steam_sessions_device").on(table.deviceId),
  index("idx_steam_sessions_steam").on(table.steamId),
]);
