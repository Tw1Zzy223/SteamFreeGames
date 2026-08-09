CREATE TABLE `price_history` (
	`app_id` text NOT NULL,
	`region` text NOT NULL,
	`currency` text NOT NULL,
	`initial_cents` integer NOT NULL,
	`final_cents` integer NOT NULL,
	`discount_percent` integer NOT NULL,
	`captured_at` integer NOT NULL,
	PRIMARY KEY(`app_id`, `region`, `captured_at`)
);
--> statement-breakpoint
CREATE INDEX `idx_price_history_app_region_time` ON `price_history` (`app_id`,`region`,`captured_at`);--> statement-breakpoint
CREATE TABLE `steam_sync` (
	`steam_id` text PRIMARY KEY NOT NULL,
	`payload` text DEFAULT '{}' NOT NULL,
	`updated_at` integer NOT NULL
);
