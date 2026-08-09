import { index, integer, primaryKey, sqliteTable, text } from "drizzle-orm/sqlite-core";

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

export const priceHistory = sqliteTable("price_history", {
  appId: text("app_id").notNull(),
  region: text("region").notNull(),
  currency: text("currency").notNull(),
  initialCents: integer("initial_cents").notNull(),
  finalCents: integer("final_cents").notNull(),
  discountPercent: integer("discount_percent").notNull(),
  capturedAt: integer("captured_at", { mode: "timestamp_ms" }).notNull(),
}, (table) => [
  primaryKey({ columns: [table.appId, table.region, table.capturedAt] }),
  index("idx_price_history_app_region_time").on(table.appId, table.region, table.capturedAt),
]);
