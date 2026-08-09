CREATE TABLE `steam_auth_states` (
	`nonce` text PRIMARY KEY NOT NULL,
	`device_id` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `idx_steam_auth_created` ON `steam_auth_states` (`created_at`);--> statement-breakpoint
CREATE TABLE `steam_sessions` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`device_id` text NOT NULL,
	`steam_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`last_used_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `idx_steam_sessions_device` ON `steam_sessions` (`device_id`);--> statement-breakpoint
CREATE INDEX `idx_steam_sessions_steam` ON `steam_sessions` (`steam_id`);