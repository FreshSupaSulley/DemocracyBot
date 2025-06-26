package democracy;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.login.LoginException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.supasulley.utils.ErrorAppender;
import com.supasulley.utils.JsonUtils;

import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import kotlin.text.Charsets;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
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
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.JDAImpl;

public class DMain {
	
	public static final String DEFAULT_PREFIX = "!", ERROR_MSG = "<@" + DMain.OWNER_ID + "> hey dumbass your bot broke";
	
	// Ignore the tokens.txt file in .gitignore
	private static final String DEMOCRACY_BOT_TOKEN;
	private static final String WEEVE_BOT_TOKEN;
	private static final String WEEVE_OWNER_ID;
	
	public static final String GITHUB_ACCESS_TOKEN;
	
	private static List<Command> commands;
	
	public static String BOT_NAME;
	public static long BOT_ID;
	public static final long OWNER_ID = 276886864525262849L;
	
	private static JDAImpl jda;
	private static PrivateChannel privateChannel;
	
	public static final Logger log = (Logger) LoggerFactory.getLogger(DMain.class);
	
	private static final File democracyDir = new File("botData");
	private static final File serverFile = new File(DMain.democracyDir.getPath() + "/serverData.txt");
	
	// Member data absolute path on pi: /root/Desktop/botData/serverData.txt
	public static final boolean inIDE;
	
	// Server Data
	public static long SERVER_ID = 1102048289202917441L;
	public static long THE_CONSTIPATION = 1102051128067248169L, AMENDMENTS = 1102051223277928509L, COMMANDERS_AND_QUEEFS = 1303051774969512059L, VOTING_BOOTH = 1102051068969504768L, VOTE_PROPOSAL = 1102051099394969750L, TEST_CHANNEL = 1105627214587904010L;
	public static long THE_WHITE_HOUSE_CATEGORY = 1102050716819918948L, MAGNA_FARTA_CATEGORY = 1102050764756635668L;
	
	// Roles
	public static long THE_MILITARY_ID = 1102048289202917442L, THE_PRESIDENT_ID = 1102055622981206086L, VOTER_ID = 1102055806159028347L;
	public static Role THE_MILITARY, THE_PRESIDENT, IMMIGRANT, VOTER;
	
	// Debug booleans
	private static boolean debug = true;
	private static boolean viewStats = false;
	
	public static Server server;
	
	static
	{
		// Load tokens
		String raw = null;
		
		try {
			raw = IOUtils.toString(DMain.class.getClassLoader().getResourceAsStream("tokens.txt"), Charsets.UTF_8);
		} catch(IOException e) {
			log.error("Failed to read tokens.txt", e);
			e.printStackTrace();
			System.exit(1);
		}
		
		JsonObject result = JsonUtils.parse(raw).getAsJsonObject();
		
		// You don't need test tokens btw for a test bot
		DEMOCRACY_BOT_TOKEN = result.get("democracy").getAsString();
		WEEVE_BOT_TOKEN = result.get("weeve").getAsString();
		WEEVE_OWNER_ID = result.get("weeve_owner_id").getAsString();
		GITHUB_ACCESS_TOKEN = result.get("github_access_token").getAsString();
		
		// If we're in a jar file, set debug to false
		String resource = DMain.class.getResource("DMain.class").toString();
		if(resource.startsWith("jar:") || resource.startsWith("rsrc:")) inIDE = false;
		else inIDE = true;
		
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
	public DMain()
	{
		if(debug)
		{
			// Set all channels to the test channel
			THE_CONSTIPATION = TEST_CHANNEL;
			AMENDMENTS = TEST_CHANNEL;
			COMMANDERS_AND_QUEEFS = TEST_CHANNEL;
			VOTING_BOOTH = TEST_CHANNEL;
			VOTE_PROPOSAL = TEST_CHANNEL;
		}
		
		// JDA will reconnect after a very long period of downtime (I tested up to 3-4 hours)
		// JDA will immediately fail if you try to create the bot when the internet is unavailable
		JDABuilder builder = JDABuilder.createLight(DEMOCRACY_BOT_TOKEN).setAutoReconnect(true).enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT).setContextMap(null);
		int attempts = 0;
		
		for(; jda == null; attempts++)
		{
			try {
				jda = (JDAImpl) builder.build().awaitReady();
			} catch(ErrorResponseException t) {
				if(t.getErrorCode() != -1) {
					t.printStackTrace();
					break;
				}
				
				// Wait until we try again
				try {
					System.out.println("Failed to connect to JDA. Retrying in 30s...");
					Thread.sleep(30000);
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			} catch(Throwable t) {
				DMain.log.error("Something went wrong booting JDA", t);
				break;
			}
		}
		
		// If still not connected
		if(jda == null || jda.getStatus() != Status.CONNECTED)
		{
			DMain.log.error("Could not connect to JDA");
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
		
		// Public slash commands
		CommandData[] publicCommands = new CommandData[] {
//			Commands.slash("timeout", "Timeout a member (president only)").addOption(OptionType.USER, "user", "User to timeout").addOptions(new OptionData(OptionType.INTEGER, "days", "Number of days", false).setMinValue(1).setMaxValue(Member.MAX_TIME_OUT_LENGTH), new OptionData(OptionType.INTEGER, "hours", "Number of hours", false).setMinValue(1).setMaxValue(Member.MAX_TIME_OUT_LENGTH * 24), new OptionData(OptionType.INTEGER, "minutes", "Number of minutes", false).setMinValue(1).setMaxValue(Member.MAX_TIME_OUT_LENGTH * 24 * 60)),
//			Commands.slash("kick", "Kick a member (president only)").addOption(OptionType.USER, "user", "User to kick"),
			Commands.slash("campaign", "Run for President").addOption(OptionType.ROLE, "party", "Your political party", true).addOptions(new OptionData(OptionType.STRING, "slogan", "Your campaign slogan", true).setMaxLength(Math.min(200, OptionData.MAX_STRING_OPTION_LENGTH))),
			Commands.slash("slogan", "Change your slogan").addOptions(new OptionData(OptionType.STRING, "slogan", "Your new slogan", true).setMaxLength(Math.min(200, OptionData.MAX_STRING_OPTION_LENGTH))),
			Commands.slash("next-election", "Returns next election time"),
			Commands.slash("propose", "Propose an amendment").addOptions(new OptionData(OptionType.STRING, "amendment", "The amendment to add", true).setMaxLength(MessagePoll.MAX_QUESTION_TEXT_LENGTH - Poll.POLL_QUESTION_PREFIX)), // Takeaway some characters for prefix
			Commands.slash("repeal", "Repeal / unrepeal an amendment").addOptions(new OptionData(OptionType.INTEGER, "amendment-number", "The amendment number to repeal", true).setMinValue(1)),
			Commands.slash("impeach", "Impeach the President").addOptions(new OptionData(OptionType.STRING, "reason", "Why impeachment is deserved", true).setMaxLength(MessagePoll.MAX_QUESTION_TEXT_LENGTH - Poll.POLL_QUESTION_PREFIX)),
//			Commands.slash("secret", "Add a word to be a secret command").addOptions(new OptionData(OptionType.STRING, "word", "The word to become the secret command", true), new OptionData(OptionType.STRING, "response", "The response to the new command", true)),
//			Commands.slash("all-secrets", "Lists all secrets (prepare for bad words)"),
//			Commands.slash("unsecret", "Remove a secret command").addOptions(new OptionData(OptionType.STRING, "word", "The secret word to remove", true)),
//			Commands.slash("resecret", "Adds back an unsecreted command").addOptions(new OptionData(OptionType.STRING, "word", "The secret word to add back", true)),
			
			// Presidential commands
//			Commands.slash("president", "Presidental commands").addSub.addOptions(new OptionData(OptionType.STRING, "word", "The word to remove from commands", true)),
		};
		
		// Update public commands
		if(!inIDE)
		{
			log.info("Updating slash commands");
			DMain.commands = jda.updateCommands().addCommands(publicCommands).complete();
		}
		else
		{
			DMain.commands = jda.retrieveCommands().complete();
		}
		
		BOT_NAME = jda.getSelfUser().getName();
		jda.getPresence().setPresence(Activity.of(ActivityType.WATCHING, "Democracy thrive"), false);
		
		BOT_ID = jda.getSelfUser().getIdLong();
		
		// Create InputListener
		CustomListener listener;
		
		try {
			listener = loadServerData(attempts);
		} catch(Throwable t) {
			DMain.log.error("Something went wrong booting server. Bot is in recovery mode", t);
			listener = new GenericEventHandler(jda);
		}
		
		// Hot fix CAQ template
//		Guild guild = jda.getGuildById(DMain.SERVER_ID);
//		Message hi = guild.getTextChannelById(1303051774969512059L).retrieveMessageById(1332187594968272918L).complete();
//		MessageEmbed embed = hi.getEmbeds().get(0);
//		EmbedBuilder builder2 = new EmbedBuilder(embed);
//		builder2.setDescription("<@" + 269872388064280581L + ">" + " of **" + MarkdownSanitizer.sanitize("circle of the wanker 7") + "**\n\n*\"" + "I promise to the people of this wonderful server that I will do everything in my power to make it worse. Much worse." + "\"*");
//		hi.editMessageEmbeds(builder2.build()).complete();
//		System.exit(1);
		
		jda.addEventListener(listener);
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
		Guild guild = jda.getGuildById(DMain.SERVER_ID);
		
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
		
		// Initialize roles
		THE_MILITARY = guild.getRoleById(THE_MILITARY_ID);
		THE_PRESIDENT = guild.getRoleById(THE_PRESIDENT_ID);
		VOTER = guild.getRoleById(VOTER_ID);
		
		// Get current president
		Member president = null;
		
		// Get President
		// Says you can't use .get but fuck you
		List<Member> presidents = guild.findMembersWithRoles(THE_PRESIDENT).get();
		
		if(presidents.size() == 1)
			president = presidents.get(0);
		else if(presidents.size() > 1)
			throw new IllegalStateException("More than 1 President exists!");
		
		log.info("President: " + (president != null ? president.getEffectiveName() : "does not exist"));
		
		// Reset file
		InputStream resetFile = getClass().getClassLoader().getResourceAsStream("serverData.txt");
		boolean usingResetFile = resetFile != null;
		
		// Check for reset data as an internal resource
		// Both files MUST exist
		if(!usingResetFile && !serverFile.exists())
		{
			throw new IllegalStateException("Server file doesn't exist, and reset file wasn't provided");
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
				DMain.log.info("Waiting for operator to confirm resetting data");
				
				tempListener.addButtonAction(message, (event) ->
				{
					confirmReset.set(event.getButton().getId().equals("yes"));
					latch.countDown();
					DMain.log.info("Operator responded! Reset value: " + confirmReset.get());
					
					// Respond to button press event
					event.getMessage().delete().queue();
					event.reply("Reset data set to " + confirmReset.get()).setEphemeral(true).queue();
				});
			}, failure -> {
				DMain.log.error("Failed to send confirmation to operator");
				latch.countDown();
			});
			
			// Wait until we get a response. Add a timeout in case nothing happens
			try {
				latch.await(2, TimeUnit.MINUTES);
			} catch(InterruptedException e) {
				DMain.log.error("Something went wrong with the countdown latch {}", e);
			}
			
			// Update usingResetFile
			usingResetFile = confirmReset.get();
			jda.removeEventListener(tempListener);
		}
		
		// Use reset file if not there
		String toRead = usingResetFile ? IOUtils.toString(resetFile, Charsets.UTF_8) : readServerData();
		
		if(usingResetFile && !inIDE)
		{
			// Send current data to logs and operator just in case
			DMain.log.info("Using reset file. Current data before reset:");
			DMain.log.info(toRead);
			DMain.sendServerData();
		}
		
		// Deserialize (test if data is null or empty)
		DMain.server = JsonUtils.deserialize(Server.class, toRead);
		
		// Can be null if the read file is blank
		if(DMain.server == null)
		{
			throw new IllegalStateException("Server file seems to be blank as server == null");
		}
//		if(president == null)
//		{
//			DMain.sendToOperator("No President could be found");
//			DMain.server.updatePresident(0);
//		}
		
		updateServerData();
		privateChannel.sendMessage(DMain.BOT_NAME + " is online (reset file == **" + (usingResetFile) + "**, attempts == **" + attempts + "**) running Java version " + System.getProperty("java.version")).complete();
		return new EventHandler(jda);
	}
	
	/**
	 * Updates the local server data file. Do this after any important changes to {@linkplain Server} variables. Locks the file during writing.
	 */
	public static void updateServerData()
	{
		// For recovery mode
		if(!DMain.serverFile.exists())
		{
			DMain.log.warn("Refusing to update server data, serverFile doesn't exist");
			return;
		}
		
		DMain.log.info("Updating serverData...");
		
		File tempFile = new File(DMain.serverFile.getParent(), "serverData.tmp");
		
		try(FileOutputStream stream = new FileOutputStream(DMain.serverFile); FileLock lock = stream.getChannel().lock(); BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile)))
		{
			// Write the updated data to the temporary file
			String newData = DMain.server.toString();
			writer.write(newData);
			writer.flush();
			
			// Rename temp file to the original file, atomic operation
			Files.move(tempFile.toPath(), serverFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			tempFile.delete();
		} catch(Exception e) {
			DMain.log.error("Could not update server data", e);
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
		if(!DMain.serverFile.exists())
		{
			DMain.log.warn("Refusing to read server data, serverFile doesn't exist");
			return null;
		}
		
		try(FileInputStream stream = new FileInputStream(DMain.serverFile); FileLock lock = stream.getChannel().lock(0, Long.MAX_VALUE, true)) {
			return Files.readString(DMain.serverFile.toPath(), StandardCharsets.UTF_8);
		} catch(Throwable t) {
			DMain.log.error("Failed to read server data", t);
		}
		
		return null;
	}
	
	public static void sendServerData()
	{
		privateChannel.sendFiles(FileUpload.fromData(serverFile)).complete();
	}
	
	// TODO: Figure out why this doesn't print an error at all. Like it seems throwable is never called
	// Maybe it was the lack of the 0 - Message.MAX_CONTENT_LENGTH?
	public static void sendToOperator(String text)
	{
		privateChannel.sendMessage(text.substring(0, Math.min(text.length(), Message.MAX_CONTENT_LENGTH))).queue(null, (throwable) ->
		{
			// If an error occurred, don't error log it again. It would create an endless loop
			DMain.log.info("Failed to send {} to operator", text, throwable);
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
		
		for(Command command : DMain.commands)
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
			DMain.log.error("Failed to find command by name {}", fullCommandName);
			return "/" + fullCommandName;
		}
		
		return "</" + result.getFullCommandName() + ":" + result.getId() + ">";
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException
	{
		if(!inIDE)
		{
			Thread runnable = new Thread()
			{
				@Override
				public void run()
				{
					try {
						// Set logback to use internal one
						io.github.freshsupasulley.main.Main.main(new String[] {"--token=" + WEEVE_BOT_TOKEN, "--owner_id=" + WEEVE_OWNER_ID, "--notify_errors"});
					} catch(Throwable t) {
						DMain.log.error("Failed to start weeve", t);
					}
				}
			};
			
			// No need to set another default uncaught exception handler, handled in static of DMain
			runnable.setName("weeve");
			runnable.start();
		}
		else
		{
			DMain.log.info("In IDE, not running weeve");
		}
		
		new DMain();
	}
}
