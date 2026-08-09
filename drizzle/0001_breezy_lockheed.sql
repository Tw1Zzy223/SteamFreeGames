CREATE INDEX `idx_support_created_at` ON `support_messages` (`created_at`);--> statement-breakpoint
CREATE INDEX `idx_support_device_created` ON `support_messages` (`device_id`,`created_at`);