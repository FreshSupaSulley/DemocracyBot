package democracy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.supasulley.utils.ErrorAppender;
import com.supasulley.utils.JsonUtils;

import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.JDAImpl;

public class DMain {
	
	public static final String DEFAULT_PREFIX = "!", ERROR_MSG = "<@" + DMain.OWNER_ID + "> hey dumbass your bot broke";
	
	// Ignore the tokens.txt file in .gitignore
	private static final String DEMOCRACY_BOT_TOKEN;
	private static final String WEEVE_BOT_TOKEN;
	private static final String WEEVE_OWNER_ID;
	
	private static List<Command> commands;
	
	public static String BOT_NAME;
	public static long BOT_ID;
	public static final long OWNER_ID = 276886864525262849L;
	
	private static JDAImpl jda;
	private static PrivateChannel privateChannel;
	private static final int MAX_CONSECUTIVE_ERRORS = 10;
	private static final int CONSECUTIVE_INTERVAL = 1000;
	private static final int DECREASE_RATE = CONSECUTIVE_INTERVAL * 100;
	private static long lastError = System.currentTimeMillis();
	private static int consecutiveErrors;
	
	public static final Logger log = (Logger) LoggerFactory.getLogger(DMain.class);
	
	public static final File democracyDir = new File("botData");
	public static final File serverFile;
	
	public static final File JAR_FILE = new File(System.getProperty("user.home") + "/Desktop/DemocracyBot.jar");
	
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
		String raw = loadAsString(new BufferedReader(new InputStreamReader(DMain.class.getClassLoader().getResourceAsStream("tokens.txt"))));
		JsonObject result = JsonUtils.parse(raw).getAsJsonObject();
		
		// You don't need test tokens btw for a test bot
		DEMOCRACY_BOT_TOKEN = result.get("democracy").getAsString();
		WEEVE_BOT_TOKEN = result.get("weeve").getAsString();
		WEEVE_OWNER_ID = result.get("weeve_owner_id").getAsString();
		
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
		
		serverFile = new File(DMain.democracyDir.getPath() + "/serverData.txt");
		
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
	
	public DMain()
	{
		// Set up directories, read from files, etc.
		initialize();
	}
	
	/**
	 * Updates the local server data file. Do this after any important changes to {@linkplain Server} variables.
	 */
	public static void updateServerData()
	{
		// For recovery mode
		if(!DMain.serverFile.exists())
		{
			log.warn("Refusing to update server data, serverFile doesn't exist");
			return;
		}
		
		log.info("Updating serverData...");
		
		try {
			// This can throw errors! We need this to fail before we open FileWriter
			String newData = DMain.server.toString();
			
			FileWriter writer = new FileWriter(DMain.serverFile);
			writer.write(newData);
			writer.close();
		} catch(Throwable t) {
			DMain.log.error("Could not write to serverData file", t);
			throw new IllegalStateException(t); // transform to runtime exception
		}
	}
	
	public static void sendServerData()
	{
		privateChannel.sendFiles(FileUpload.fromData(serverFile)).complete();
	}
	
	public static void sendToOperator(String text)
	{
		privateChannel.sendMessage(text).queue(null, (throwable) ->
		{
			// If an error occurred, don't error log it again. It would create an endless loop
			DMain.log.info("Failed to send {} to operator", text);
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
	 * Creates directories, files, and reads server data before program begins.
	 * 
	 * @throws IOException
	 * @throws LoginException
	 * @throws InterruptedException
	 */
	private CustomListener initialize()
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
		ErrorAppender.setErrorCallback(message -> forwardError(message.getMessage()));
		
		// Public slash commands
		CommandData[] publicCommands = new CommandData[] {
//											publicCommands[0] = Commands.slash("violation", "Report a violation of the rules").addOption(OptionType.USER, "violator", "The one who violated the rules", true).addOptions(new OptionData(OptionType.INTEGER, "minutes", "prison time of violator").setRequiredRange(1, 3));
//											publicCommands[1] = Commands.slash("impeach", "Impeach the President");
			Commands.slash("campaign", "Run for President").addOption(OptionType.ROLE, "party", "Your political party", true).addOptions(new OptionData(OptionType.STRING, "slogan", "Your campaign slogan", true).setMaxLength(Math.min(200, OptionData.MAX_STRING_OPTION_LENGTH))),
			Commands.slash("slogan", "Change your slogan").addOption(OptionType.STRING, "slogan", "Your new slogan", true),
			Commands.slash("next-election", "Returns next election time"),
			Commands.slash("propose", "Propose an amendment").addOptions(new OptionData(OptionType.STRING, "amendment", "The amendment to add", true).setMaxLength(MessagePoll.MAX_QUESTION_TEXT_LENGTH)),
			Commands.slash("repeal", "Repeal / unrepeal an amendment").addOptions(new OptionData(OptionType.INTEGER, "amendment-number", "The amendment number to repeal", true).setMinValue(1)),
			Commands.slash("impeach", "Impeach the President"),
//			publicCommands[6] = Commands.slash("party", "View political party commands").addSubcommands(new SubcommandData("create", "Create a political party").addOption(OptionType.STRING, "name", "Name of the party", true), new SubcommandData("join", "Join a political party").addOption(OptionType.ROLE, "party", "The party to join", true), new SubcommandData("leave", "Leave a political party").addOption(OptionType.ROLE, "party", "The party to leave", true));
//			publicCommands[6] = Commands.slash("archive", "Propose addition to the Library of Congress").addOption(OptionType.STRING, "entry", "The library of congress entry to add", true);
			Commands.slash("secret", "Add a word to be a secret command").addOptions(new OptionData(OptionType.STRING, "word", "The word to become the command", true), new OptionData(OptionType.STRING, "response", "The response to the new command", true)),
			Commands.slash("unsecret", "Remove a word from secret commands").addOptions(new OptionData(OptionType.STRING, "word", "The word to remove from commands", true)),
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
		
		jda.addEventListener(listener);
		return listener;
	}
	
	/**
	 * Sends errors to operator if enabled.
	 * 
	 * @param error the error to send
	 */
	private void forwardError(String error)
	{
		// Get time between errors
		long current = System.currentTimeMillis();
		long distance = current - lastError;
		
		// If this error occurred too soon after the last
		if(distance < CONSECUTIVE_INTERVAL)
		{
			// If the consecutive errors have reached the maximum allowed
			if(++DMain.consecutiveErrors == MAX_CONSECUTIVE_ERRORS)
			{
				// Warn the owner that something is definitely wrong
				sendToOperator("Too many consecutive errors. Check logs.");
			}
		}
		// If we haven't had an error in a while
		else
		{
			// 100 seconds needs to pass to decrease consecutive errors by 1
			DMain.consecutiveErrors = Math.max(0, DMain.consecutiveErrors = (int) (distance / DECREASE_RATE));
		}
		
		// Only DM the user if we're under the max
		if(DMain.consecutiveErrors < MAX_CONSECUTIVE_ERRORS)
		{
			sendToOperator("Error (" + DMain.consecutiveErrors + "/" + MAX_CONSECUTIVE_ERRORS + " consecutive):\n" + error);
			DMain.lastError = System.currentTimeMillis();
		}
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
		
		// Use reset file if not there
		String toRead = loadAsString(new BufferedReader(new InputStreamReader(usingResetFile ? resetFile : new FileInputStream(serverFile))));
		
		// If we're using the reset file
		if(usingResetFile)
		{
			// Send the old file
			log.info("Using reset file");
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
	
	private static String loadAsString(BufferedReader reader)
	{
		if(reader == null) return "";
		
		StringBuilder builder = new StringBuilder();
		
		try {
			String initString = reader.readLine();
			if(initString == null) return "";
			builder.append(initString);
			
			for(String line = null; (line = reader.readLine()) != null;)
			{
				builder.append("\n");
				builder.append(line);
			}
		} catch(IOException e) {
			System.err.println("An error occured loading resource as string");
			e.printStackTrace();
		}
		
		return builder.toString();
	}
	
	public static void main(String[] args) throws FileNotFoundException
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
						com.supasulley.main.Main.main(new String[] {"--token=" + WEEVE_BOT_TOKEN, "--owner_id=" + WEEVE_OWNER_ID, "--notify_errors"});
					} catch(Throwable t) {
						DMain.log.error("Failed to start weeve", t);
					}
				}
			};
			
			// No need to set another default uncaught exception handler, handled in static of DMain
			runnable.setName("weeve");
			runnable.start();
		}
		
		new DMain();
	}
}
