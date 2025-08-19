package io.github.freshsupasulley.dbot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import io.github.freshsupasulley.dbot.polls.Poll;
import io.github.freshsupasulley.dbot.utils.ErrorAppender;
import io.github.freshsupasulley.dbot.utils.JsonUtils;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.ICommandReference;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.JDAImpl;

public class Main {
	
	public static final String DEFAULT_PREFIX = "!", ERROR_MSG = "<@" + Main.OWNER_ID + "> hey dumbass your bot broke";
	
	private static String DEMOCRACY_BOT_TOKEN;
	public static String GITHUB_ACCESS_TOKEN;
	
	private static List<Command> commands;
	
	public static String BOT_NAME;
	public static long BOT_ID;
	public static final long OWNER_ID = 276886864525262849L;
	
	private static JDAImpl jda;
	private static PrivateChannel privateChannel;
	
	public static final Logger log = (Logger) LoggerFactory.getLogger(Main.class);
	
	private static final File democracyDir = new File("botData");
	private static final File serverData = new File(Main.democracyDir.getPath() + "/serverData.json");
	
	// Member data absolute path on pi: /root/Desktop/botData/serverData.json
	public static final boolean inIDE;
	
	// Debug booleans
	private static boolean debug = true;
	private static boolean viewStats = false;
	
	public static Server server;
	
	static
	{
		// If we're in a jar file, set debug to false
		String resource = Main.class.getResource("Main.class").toString();
		if(resource.startsWith("jar:") || resource.startsWith("rsrc:"))
			inIDE = false;
		else
			inIDE = true;
		
		if(inIDE)
		{
			log.info("Running in IDE, using temporary directories");
		}
		else
		{
			debug = false;
			viewStats = false;
		}
		
		// Create necessary files
		log.info((democracyDir.mkdirs() ? "Created BotData directories!" : "Did not need to create botData directories") + " - " + democracyDir.getAbsolutePath());
		
		// Uncaught errors are sent to DemocracyBot logs. This catches weeve errors too
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler()
		{
			
			@Override
			public void uncaughtException(Thread t, Throwable e)
			{
				log.error("Uncaught exception", e);
			}
		});
		
		log.info("STARTING DBOT");
	}
	
	/**
	 * Creates directories, files, and reads server data before program begins.
	 *
	 * @throws IOException
	 * @throws LoginException
	 * @throws InterruptedException
	 */
	public Main()
	{
		// if(debug)
		// {
		// // Set all channels to the test channel
		// THE_CONSTIPATION = TEST_CHANNEL;
		// AMENDMENTS = TEST_CHANNEL;
		// COMMANDERS_AND_QUEEFS = TEST_CHANNEL;
		// VOTING_BOOTH = TEST_CHANNEL;
		// VOTE_PROPOSAL = TEST_CHANNEL;
		// }
		
		// JDA will reconnect after a very long period of downtime (I tested up to 3-4 hours)
		// JDA will immediately fail if you try to create the bot when the internet is unavailable
		// setting eventPassthrough so I can see raw data when debugging (ONLY IN IDE THOUGH!)
		JDABuilder builder = JDABuilder.createLight(DEMOCRACY_BOT_TOKEN).setAutoReconnect(true).enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT).setContextMap(null).setEventPassthrough(inIDE);
		int attempts = 0;
		
		for(; jda == null; attempts++)
		{
			try
			{
				jda = (JDAImpl) builder.build().awaitReady();
			} catch(ErrorResponseException t)
			{
				if(t.getErrorCode() != -1)
				{
					t.printStackTrace();
					break;
				}
				
				// Wait until we try again
				try
				{
					System.out.println("Failed to connect to JDA. Retrying in 30s...");
					Thread.sleep(30000);
				} catch(InterruptedException e)
				{
					e.printStackTrace();
				}
			} catch(Exception e)
			{
				Main.log.error("Something went wrong booting JDA", e);
				break;
			}
		}
		
		// If still not connected
		if(jda == null || jda.getStatus() != Status.CONNECTED)
		{
			Main.log.error("Could not connect to JDA");
			System.exit(1);
		}
		
		// Open error channel to developer for debugging / daily logs
		privateChannel = jda.retrieveUserById(OWNER_ID).complete().openPrivateChannel().complete();
		ErrorAppender.setErrorCallback((consecutiveErrors, message) ->
		{
			// Warn the owner that something is definitely wrong
			if(consecutiveErrors == 10)
			{
				sendToOperator("Too many consecutive errors. Check logs.");
			}
			else if(consecutiveErrors < 10)
			{
				// Cut it off at 500 characters, then bring it back to a newline
				String sample = ThrowableProxyUtil.asString(message.getThrowableProxy()).substring(0, 1000);
				int lastNewLine = sample.lastIndexOf("\n");
				sample = sample.substring(0, lastNewLine == -1 ? sample.length() : lastNewLine);
				
				// Only DM the user if we're under the max
				sendToOperator("An error occurred (" + consecutiveErrors + " consecutive):\n```" + message.getFormattedMessage() + ": " + sample + "```");
			}
		});
		
		OptionData name = new OptionData(OptionType.STRING, "name", "Name of your party", true).setMaxLength(50);
		OptionData color = new OptionData(OptionType.STRING, "color", "Color name or HEX (e.g., FF5733) without number sign (#)", false);
		
		// max length of a role is 100 apparently (hard-coded in JDA but not a static constant we can pull from). 50 it is
		SubcommandData[] partySubcommands = {new SubcommandData("create", "Create a party").addOptions(name, color), new SubcommandData("join", "Join a party").addOptions(new OptionData(OptionType.ROLE, "party", "The party to join", true)), new SubcommandData("leave", "Leave your party"), new SubcommandData("info", "View party info").addOptions(new OptionData(OptionType.ROLE, "party", "The party to lookup", true))};
		
		// Subcommand group
		SubcommandData[] partyEditSubcommands = new SubcommandData[] {new SubcommandData("name", "Change party name").addOptions(name), new SubcommandData("color", "Change party color").addOptions(shallowClone(color).setRequired(true)), new SubcommandData("ban", "Ban a member").addOptions(new OptionData(OptionType.USER, "user", "User to ban", true)), new SubcommandData("unban", "Unban a member").addOptions(new OptionData(OptionType.USER, "user", "User to ban", true)), new SubcommandData("invite-bot", "Invites a bot to the party").addOptions(new OptionData(OptionType.USER, "bot", "Bot to join", true)), new SubcommandData("transfer", "Elect a new party leader").addOptions(new OptionData(OptionType.USER, "user", "User to transfer the party to", true)),
		};
		
		// Public slash commands
		CommandData[] publicCommands = new CommandData[] {Commands.slash("party", "Party commands").addSubcommands(partySubcommands).addSubcommandGroups(new SubcommandGroupData("edit", "Party editing commands").addSubcommands(partyEditSubcommands)), Commands.slash("campaign", "Run for President").addOptions(new OptionData(OptionType.STRING, "slogan", "Your campaign slogan", true).setMaxLength(Math.min(200, OptionData.MAX_STRING_OPTION_LENGTH))), // in case it changes
											Commands.slash("slogan", "Change your slogan").addOptions(new OptionData(OptionType.STRING, "slogan", "Your new slogan", true).setMaxLength(Math.min(200, OptionData.MAX_STRING_OPTION_LENGTH))), Commands.slash("next-election", "Returns next election time"), Commands.slash("propose", "Propose an amendment").addOptions(new OptionData(OptionType.STRING, "amendment", "The amendment to add (markdown will be escaped)", true).setMaxLength(MessagePoll.MAX_QUESTION_TEXT_LENGTH - Poll.POLL_QUESTION_PREFIX)), // Takeaway some characters for prefix
											Commands.slash("repeal", "Repeal / unrepeal an amendment").addOptions(new OptionData(OptionType.INTEGER, "amendment-number", "The amendment number to repeal", true).setMinValue(1)), Commands.slash("refer", "Sends the amendment in chat").addOptions(new OptionData(OptionType.INTEGER, "amendment-number", "The amendment number to refer to", true).setMinValue(1)), Commands.slash("impeach", "Impeach the President").addOptions(new OptionData(OptionType.STRING, "reason", "Why impeachment is deserved", true).setMaxLength(MessagePoll.MAX_QUESTION_TEXT_LENGTH - Poll.POLL_QUESTION_PREFIX)), Commands.slash("naturalize", "Naturalize an immigrant").addOptions(new OptionData(OptionType.USER, "user", "The user to naturalize", true))
		};
		
		boolean updateCommands = false;
		
		// Update public commands
		if(!inIDE || updateCommands)
		{
			log.info("Updating slash commands");
			Main.commands = jda.updateCommands().addCommands(publicCommands).complete();
		}
		else
		{
			Main.commands = jda.retrieveCommands().complete();
		}
		
		BOT_NAME = jda.getSelfUser().getName();
		jda.getPresence().setPresence(Activity.of(ActivityType.WATCHING, "Democracy thrive"), false);
		
		BOT_ID = jda.getSelfUser().getIdLong();
		
		// Create InputListener
		CustomListener listener;
		
		try
		{
			// Create bot data if we need to
			Main.serverData.createNewFile();
			listener = loadServerData(attempts);
		} catch(Throwable t)
		{
			Main.log.error("Something went wrong booting server. Bot is in recovery mode", t);
			sendToOperator("Can't boot: " + t.getLocalizedMessage());
			listener = new GenericEventHandler(jda);
		}
		
		jda.addEventListener(listener);
	}
	
	/**
	 * Clones an {@link OptionData} instance without copying everything.
	 *
	 * @param original data to copy
	 * @return shallow clone
	 */
	private OptionData shallowClone(OptionData original)
	{
		OptionData copy = new OptionData(original.getType(), original.getName(), original.getDescription(), original.isRequired(), original.isAutoComplete());
		copy.addChoices(original.getChoices());
		return copy;
	}
	
	/**
	 * Loads the server data.
	 *
	 * @param attempts number of tries it took to boot JDA
	 * @return new {@linkplain CustomListener} instance
	 * @throws IOException if something went wrong
	 */
	private CustomListener loadServerData(int attempts) throws IOException
	{
		// ~~Reset file (named differently than serverData to avoid confusion with botData/serverData, which is only generated when testing dbot in the IDE)~~
		// ^ nah
		InputStream resetFile = getClass().getClassLoader().getResourceAsStream("serverData.json");
		boolean usingResetFile = resetFile != null;
		
		// Check for reset data as an internal resource
		// At least one of these files MUST exist
		if(!usingResetFile && !serverData.exists())
		{
			throw new IllegalStateException("Server file doesn't exist, and reset file wasn't provided. Make sure to provide `serverData.json` in src/main/resources when testing!");
		}
		
		// If we're using the reset file
		if(usingResetFile && !inIDE)
		{
			// Hold program until operator confirms it
			CountDownLatch latch = new CountDownLatch(1);
			
			// Default behavior should be to NOT use reset data
			AtomicBoolean confirmReset = new AtomicBoolean(false);
			
			GenericEventHandler tempListener = new GenericEventHandler(jda);
			jda.addEventListener(tempListener);
			
			privateChannel.sendMessage("Reset file was provided. Is this correct?\n*Local data will be used if you don't respond.*").addActionRow(Button.primary("yes", "Yes"), Button.danger("no", "No")).queue(message ->
			{
				Main.log.info("Waiting for operator to confirm resetting data");
				
				tempListener.addButtonAction(message, (event) ->
				{
					confirmReset.set(event.getButton().getId().equals("yes"));
					latch.countDown();
					Main.log.info("Operator responded! Reset value: " + confirmReset.get());
					
					// Respond to button press event
					event.getMessage().delete().queue();
					event.reply("Reset data set to " + confirmReset.get()).setEphemeral(true).queue();
				});
			}, failure ->
			{
				Main.log.error("Failed to send confirmation to operator");
				latch.countDown();
			});
			
			// Wait until we get a response. Add a timeout in case nothing happens
			try
			{
				latch.await(2, TimeUnit.MINUTES);
			} catch(InterruptedException e)
			{
				Main.log.error("Something went wrong with the countdown latch {}", e);
			}
			
			// Update usingResetFile
			usingResetFile = confirmReset.get();
			jda.removeEventListener(tempListener);
		}
		
		// Use reset file if not there
		String toRead = usingResetFile ? new String(resetFile.readAllBytes(), StandardCharsets.UTF_8) : readServerData();
		
		if(usingResetFile && !inIDE)
		{
			// Send current data to logs and operator just in case
			Main.log.info("Using reset file. Current data before reset:");
			Main.log.info(toRead);
			Main.sendServerData();
		}
		
		// Deserialize (test if data is null or empty)
		Main.server = JsonUtils.deserialize(Server.class, toRead);
		
		// Can be null if the read file is blank
		if(Main.server == null)
		{
			throw new IllegalStateException("Server file seems to be blank as server == null");
		}
		
		Guild guild = Main.server.getServer(jda);
		
		if(viewStats)
		{
			System.out.println("*** Roles:");
			guild.getRoles().forEach(role -> System.out.println(role.getName() + " " + role.getIdLong()));
			System.out.println("*** Members:");
			guild.loadMembers().get().forEach(member -> System.out.println(member.getEffectiveName() + " " + member.getIdLong()));
			System.out.println("*** Channels:");
			guild.getChannels().forEach(channel -> System.out.println(channel.getName() + " " + channel.getIdLong()));
			System.exit(0);
		}
		
		// Get current president
		Member president = null;
		
		// Get President
		// Says you can't use .get but fuck you
		List<Member> presidents = guild.findMembersWithRoles(guild.getRoleById(Main.server.getThePresident())).get();
		
		if(presidents.size() == 1)
			president = presidents.get(0);
		else if(presidents.size() > 1)
			throw new IllegalStateException("More than 1 President exists!");
		
		log.info("President: " + (president != null ? president.getEffectiveName() : "does not exist"));
		
		updateServerData();
		privateChannel.sendMessage(Main.BOT_NAME + " is online (reset file == **" + (usingResetFile) + "**, attempts == **" + attempts + "**) running Java version " + System.getProperty("java.version")).complete();
		return new EventHandler(jda);
	}
	
	/**
	 * Updates the local server data file. Do this after any important changes to {@linkplain Server} variables. Locks the file during writing.
	 */
	public static void updateServerData()
	{
		// For recovery mode
		// ^ what does this mean? We create Main.serverData now if it doesn't exist...
		if(!Main.serverData.exists())
		{
			Main.log.warn("Refusing to update server data, serverFile doesn't exist");
			return;
		}
		
		Main.log.info("Updating serverData...");
		
		File tempFile = new File(Main.serverData.getParent(), "serverData.tmp");
		
		try(FileOutputStream stream = new FileOutputStream(Main.serverData); FileLock lock = stream.getChannel().lock(); BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile)))
		{
			// Write the updated data to the temporary file
			String newData = Main.server.toString();
			writer.write(newData);
			writer.flush();
			
			// Rename temp file to the original file, atomic operation
			Files.move(tempFile.toPath(), serverData.toPath(), StandardCopyOption.REPLACE_EXISTING);
			tempFile.delete();
		} catch(Exception e)
		{
			Main.log.error("Could not update server data", e);
		}
	}
	
	/**
	 * Loads the server data file as a string.
	 *
	 * @return server data, or null if failed
	 */
	public static String readServerData()
	{
		// For recovery mode
		if(!Main.serverData.exists())
		{
			Main.log.warn("Refusing to read server data, serverFile doesn't exist");
			return null;
		}
		
		try(FileInputStream stream = new FileInputStream(Main.serverData); FileLock lock = stream.getChannel().lock(0, Long.MAX_VALUE, true))
		{
			return Files.readString(Main.serverData.toPath(), StandardCharsets.UTF_8);
		} catch(Throwable t)
		{
			Main.log.error("Failed to read server data", t);
		}
		
		return null;
	}
	
	public static void sendServerData()
	{
		privateChannel.sendFiles(FileUpload.fromData(serverData)).complete();
	}
	
	// TODO: Figure out why this doesn't print an error at all. Like it seems throwable is never called
	// Maybe it was the lack of the 0 - Message.MAX_CONTENT_LENGTH?
	public static void sendToOperator(String text)
	{
		privateChannel.sendMessage(text.substring(0, Math.min(text.length(), Message.MAX_CONTENT_LENGTH))).queue(null, (throwable) ->
		{
			// If an error occurred, don't error log it again. It would create an endless loop
			Main.log.info("Failed to send {} to operator", text, throwable);
		});
	}
	
	public static void shutdown()
	{
		privateChannel.sendMessage("Shutting down!").complete();
		sendServerData();
		jda.shutdownNow();
		System.exit(0);
	}
	
	/**
	 * Finds the command by the full command name and returns the markdown needed to reference it in messages.
	 *
	 * @param fullCommandName {@linkplain ICommandReference#getFullCommandName()} of command
	 * @return markdown of the command, calculated by {@code "</" + result.getFullCommandName() + ":" + result.getId() + ">"}
	 */
	public static String getCommandReference(String fullCommandName)
	{
		ICommandReference result = null;
		
		for(Command command : Main.commands)
		{
			for(Subcommand subcommand : command.getSubcommands())
			{
				if(subcommand.getFullCommandName().equals(fullCommandName))
				{
					result = subcommand;
				}
			}
			
			if(command.getName().equals(fullCommandName))
			{
				result = command;
			}
		}
		
		if(result == null)
		{
			Main.log.error("Failed to find command by name {}", fullCommandName);
			return "/" + fullCommandName;
		}
		
		return "</" + result.getFullCommandName() + ":" + result.getId() + ">";
	}
	
	// Pulls tokens from gradle.properties (see build.gradle)
	public static void main(String[] args) throws FileNotFoundException, IOException
	{
		// Filled from gradle.properties -> tokens.properties
		Properties properties = new Properties();
		
		try(InputStream stream = Main.class.getClassLoader().getResourceAsStream("tokens.properties"))
		{
			properties.load(stream);
		}
		
		if(!inIDE)
		{
			Thread runnable = new Thread()
			{
				
				@Override
				public void run()
				{
					try
					{
						// Dump all props into weeve
						String[] newArgs = properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new);
						io.github.freshsupasulley.weeve.Main.main(newArgs);
					} catch(Throwable t)
					{
						Main.log.error("Failed to start weeve", t);
					}
				}
			};
			
			// No need to set another default uncaught exception handler, handled in static of DMain
			runnable.setName("weeve");
			runnable.start();
		}
		else
		{
			Main.log.info("In IDE, not running weeve");
		}
		
		DEMOCRACY_BOT_TOKEN = properties.getProperty("democracy");
		GITHUB_ACCESS_TOKEN = properties.getProperty("github_access_token");
		
		new Main();
	}
}
