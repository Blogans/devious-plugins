package net.subaru.replayer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.subaru.replayer.record.RecordClientInitializer;
import net.subaru.replayer.replay.ReplayClientInitializer;
import net.unethicalite.api.events.IsaacCipherGenerated;
import net.unethicalite.client.Static;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

@Slf4j
@PluginDescriptor(
	name = "Replay",
	description = "Replay plugin",
	tags = {"replay", "record", "playback", "replayer", "recorder"}
)
@Extension
public class ReplayPlugin extends Plugin
{
	private static final int PORT = 43594;

	@Inject
	private Client client;

	@Inject
	private WorldService worldService;

	@Inject
	private ReplayConfig config;

	private final ProxyServer proxyServer;

	private final RecordClientInitializer recordClientInitializer;
	private final ReplayClientInitializer replayClientInitializer;

	private int lastWorld = -1;

	public ReplayPlugin() {
		this.proxyServer = new ProxyServer();
		this.recordClientInitializer = new RecordClientInitializer(this);
		this.replayClientInitializer = new ReplayClientInitializer(this);
	}

	@Provides
	ReplayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ReplayConfig.class);
	}

	public static final File STORM_DIR = new File(System.getProperty("user.home"), ".storm");


	public Path getRecordingPath()
	{
		return STORM_DIR.toPath().resolve("recordings");
	}

	public Path getRecordingPath(String timestamp)
	{
		return STORM_DIR.toPath().resolve("recordings").resolve(timestamp);
	}

	public <T> T getStaticField(String className, String fieldName) throws ClassNotFoundException,
			NoSuchFieldException, IllegalAccessException {
		Class<?> clazz = this.client.getClass().getClassLoader().loadClass(className);

		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);

		return (T) field.get(null);
	}

	public static <T> T getField(Object object, String fieldName) throws NoSuchFieldException, IllegalAccessException {
		Field field = object.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);

		return (T) field.get(object);
	}

	public static Method getMethod(Object object, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Method method = object.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);

		return method;
	}

	public int[] getIsaacKey() {
        try {
            return getStaticField("client", "hi");
        } catch (Exception e) {
			log.error("Couldn't get ISAAC key", e);
			return null;
        }
    }

	public Object getNetWriter() throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException {
		return getStaticField("client", "iq");
	}

	public Object getNetWriterPacketBuffer() throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException {
		Object netWriter = this.getNetWriter();
		return getField(netWriter, "an");
	}


	public void seedIsaac(int[] key) throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
		/*Object packetBuffer = getNetWriterPacketBuffer();

		Method seedMethod = getMethod(packetBuffer, "az", int[].class, short.class);
		seedMethod.invoke(packetBuffer, key, (short) -25131);
		 */
		//Static.getClient().getPacketWriter().setIsaacCipher();

		Object packetBuffer = getNetWriterPacketBuffer();

		Method[] methods = packetBuffer.getClass().getMethods();

		for (Method method : methods) {
			if (method.getName().equals("aq")) {
				log.info("PacketBuffer method: {}", method.getName());
				log.info("Parameter types: {}", method.getParameterTypes());
				log.info("Param count: {}", method.getParameterCount());
				method.setAccessible(true);
				method.invoke(packetBuffer, key, (byte) 48);
				break;
			}
		}

		log.info("Seeded ISAAC");
		/*Method seedMethod = getMethod(packetBuffer, "aq", int[].class);
		seedMethod.invoke(packetBuffer, key);
		 */
	}

	@Override
	protected void startUp() throws Exception
	{
		lastWorld = -1;

		if (config.record()) {
			Static.getClient().getLogger().info("Starting record server");
			this.proxyServer.start(PORT, this.recordClientInitializer);
		} else {
			Path recordingsDir = getRecordingPath();
			String replayFileName = "2024-07-17_18-47-42";
			Path fullReplayPath = recordingsDir.resolve(replayFileName);
			this.replayClientInitializer.setRecordingPath(fullReplayPath);
			this.proxyServer.start(PORT, this.replayClientInitializer);
		}
	}

	@Subscribe
	private void onCipherGenerated(IsaacCipherGenerated event)
	{
		log.info("Cipher generated");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
		this.proxyServer.stop();
	}

	private World getWorldData(int worldId)
	{
		WorldResult result = worldService.getWorlds();
		if (result == null) {
			return null;
		}
		return result.findWorld(client.getWorld());
	}

	private boolean updateWorld(int worldId)
	{
		World world = this.getWorldData(worldId);
		if (world == null) {
			return false;
		}

		this.updateWorld(world);
		return true;
	}

	private void updateWorld(World world)
	{
		log.info("Updating world {}, {}", world.getId(), world.getAddress());

		final net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress("127.0.0.1");
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getLocation());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		client.changeWorld(rsWorld);

		this.recordClientInitializer.setAddress(world.getAddress());
		this.recordClientInitializer.setPort(PORT); // TODO: get this from the client
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (this.lastWorld != client.getWorld()) {
			this.updateWorld(client.getWorld());
			this.lastWorld = client.getWorld();
		}
	}
}
