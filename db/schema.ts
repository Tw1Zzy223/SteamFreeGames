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
