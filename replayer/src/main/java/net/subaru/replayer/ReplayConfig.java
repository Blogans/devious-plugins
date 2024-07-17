package net.subaru.replayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface ReplayConfig extends Config
{
	@ConfigItem(
		keyName = "record",
		name = "Record mode",
		description = ""
	)
	default boolean record()
	{
		return false;
	}
}

