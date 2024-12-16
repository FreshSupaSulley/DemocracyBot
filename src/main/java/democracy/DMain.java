package democracy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.supasulley.main.Main;
import com.supasulley.utils.ErrorHandler;
import com.supasulley.web.JsonUtils;
import com.supasulley.web.WebUtils;

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
	private static ErrorHandler errorHandler;
	public static final Logger log = (Logger) LoggerFactory.getLogger(DMain.class);
	
	public static final File democracyDir = new File("botData");
	public static final File serverFile;
	public static final File logs;
	
	public static final File JAR_FILE = new File(System.getProperty("user.home") + "/Desktop/DemocracyBot.jar");
	
	// Member data absolute path on pi: /root/Desktop/botData/serverData.txt
	public static final boolean inIDE;
	
	// Server Data
	public static long SERVER_ID = 1102048289202917441L;
	public static long THE_CONSTIPATION = 1102051128067248169L, AMENDMENTS = 1102051223277928509L, COMMANDERS_AND_QUEEFS = 1303051774969512059L, VOTING_BOOTH = 1102051068969504768L, VOTE_PROPOSAL = 1102051099394969750L, TEST_CHANNEL = 1105627214587904010L;
	public static long THE_WHITE_HOUSE_CATEGORY = 1102050716819918948L, MAGNA_FARTA_CATEGORY = 1102050764756635668L;
	
	// Roles
	public static long THE_MILITARY_ID = 1102048289202917442L, THE_PRESIDENT_ID = 1102055622981206086L, IMMIGRANT_ID = 1102055715687891084L, VOTER_ID = 1102055806159028347L;
	public static Role THE_MILITARY, THE_PRESIDENT, IMMIGRANT, VOTER;
	
	// Debug booleans
	private static boolean debug = true;
	private static boolean viewStats = false;
	
	public static Server server;
	
	static
	{
		// Load tokens
		String raw = loadAsString(new BufferedReader(new InputStreamReader(DMain.class.getClassLoader().getResourceAsStream("tokens.txt"))));
		Map<String, Object> result = WebUtils.parseJSON(WebUtils.MAP, raw);
		
		// You don't need test tokens btw for a test bot
		DEMOCRACY_BOT_TOKEN = result.get("democracy").toString();
		WEEVE_BOT_TOKEN = result.get("weeve").toString();
		WEEVE_OWNER_ID = result.get("weeve_owner_id").toString();
		
		// If we're in a jar file, set debug to false
		String resource = DMain.class.getResource("DMain.class").toString();
		if(resource.startsWith("jar:") || resource.startsWith("rsrc:")) inIDE = false;
		else inIDE = true;
		
		if(inIDE)
		{
			System.out.println("Running in IDE, using temporary directories");
		}
		else
		{
			debug = false;
			viewStats = false;
		}
		
		// Create necessary files
		System.out.println((democracyDir.mkdirs() ? "Created BotData directories!" : "Did not need to create botData directories") + " - " + democracyDir.getAbsolutePath());
		
		serverFile = new File(DMain.democracyDir.getPath() + "/serverData.txt");
		logs = new File(DMain.democracyDir.getPath() + "/DBLogs.log");
		
		try {
			System.out.println((logs.createNewFile() ? "Created Logs!" : "Did not need to create serverData") + " - " + logs.getAbsolutePath());
		} catch(IOException e) {
			System.err.println("Could not create serverData file");
			e.printStackTrace();
			System.exit(1);
		}
		
		DMain.log.info("*** PROGRAM START ***");
	}
	
	public DMain()
	{
		// Set up directories, read from files, etc.
		CustomListener listener = initialize();
		int lastHour = Integer.parseInt(getTime("HH"));
		
		// Infinite loop to send logs, update serverData, etc.
		while(true)
		{
			listener.tick();
			int currentHour = Integer.parseInt(getTime("HH"));
			
			// Check that logs aren't becoming too big
			if(logs.length() > 500000) sendLogs();
			
			if(currentHour != lastHour)
			{
				if(currentHour == 22)
				{
					updateServerData();
//					sendLogs();
				}
				
				lastHour = currentHour;
			}
			
			try {
				Thread.sleep(30000);
			} catch(InterruptedException e) {
				DMain.log(e);
			}
		}
	}
	
	public static void updateServerData()
	{
		// For recovery mode
		if(!DMain.serverFile.exists())
		{
			DMain.log("Refusing to update server data, serverFile doesn't exist");
			return;
		}
		
		DMain.log("Updating serverData...");
		
		try {
			// This can throw errors! We need this to fail before we open FileWriter
			String newData = DMain.server.toString();
			
			FileWriter writer = new FileWriter(DMain.serverFile);
			writer.write(newData);
			writer.close();
		} catch(Throwable t) {
			DMain.error("Could not write to serverData file", t);
			throw new IllegalStateException(t); // transform to runtime exception
		}
	}
	
	public static String getTime(String format)
	{
		SimpleDateFormat date = new SimpleDateFormat(format);
		return date.format(new Date(System.currentTimeMillis()));
	}
	
	public static void sendLogs()
	{
		if(logs.length() == 0)
		{
			sendToOperator("Logs are empty!");
			return;
		}
		
		DMain.log("Sending logs...");
		sendFileToOperator(logs);
	}
	
	public static void sendServerData()
	{
		sendFileToOperator(serverFile);
	}
	
	public static void sendFileToOperator(File file)
	{
		privateChannel.sendFiles(FileUpload.fromData(file)).complete();
	}
	
	public static void sendToOperator(String message)
	{
		EventHandler.sendMessage(privateChannel, message);
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
				DMain.error("Uh oh. Unexpected error");
				t.printStackTrace();
				break;
			}
		}
		
		// If still not connected
		if(jda == null || jda.getStatus() != Status.CONNECTED)
		{
			DMain.error("Could not connect to JDA");
			System.exit(1);
		}
		
		// Open error channel to developer for debugging / daily logs
		privateChannel = jda.retrieveUserById(OWNER_ID).complete().openPrivateChannel().complete();
		errorHandler = new ErrorHandler(privateChannel);
		
		// Public slash commands
		CommandData[] publicCommands = new CommandData[7];
//		publicCommands[0] = Commands.slash("violation", "Report a violation of the rules").addOption(OptionType.USER, "violator", "The one who violated the rules", true).addOptions(new OptionData(OptionType.INTEGER, "minutes", "prison time of violator").setRequiredRange(1, 3));
//		publicCommands[1] = Commands.slash("impeach", "Impeach the President");
		publicCommands[0] = Commands.slash("campaign", "Run for President").addOption(OptionType.ROLE, "party", "Your political party", true).addOptions(new OptionData(OptionType.STRING, "slogan", "Your campaign slogan", true).setMaxLength(Math.min(200, OptionData.MAX_STRING_OPTION_LENGTH)));
		publicCommands[1] = Commands.slash("slogan", "Change your slogan").addOption(OptionType.STRING, "slogan", "Your new slogan", true);
		publicCommands[2] = Commands.slash("next-election", "Returns next election time");
		publicCommands[3] = Commands.slash("propose", "Propose an amendment").addOptions(new OptionData(OptionType.STRING, "amendment", "The amendment to add", true).setMaxLength(MessagePoll.MAX_QUESTION_TEXT_LENGTH));
		publicCommands[4] = Commands.slash("repeal", "Repeal / unrepeal an amendment").addOptions(new OptionData(OptionType.INTEGER, "amendment-number", "The amendment number to repeal", true).setMinValue(1));
//		publicCommands[6] = Commands.slash("party", "View political party commands").addSubcommands(new SubcommandData("create", "Create a political party").addOption(OptionType.STRING, "name", "Name of the party", true), new SubcommandData("join", "Join a political party").addOption(OptionType.ROLE, "party", "The party to join", true), new SubcommandData("leave", "Leave a political party").addOption(OptionType.ROLE, "party", "The party to leave", true));
//		publicCommands[6] = Commands.slash("archive", "Propose addition to the Library of Congress").addOption(OptionType.STRING, "entry", "The library of congress entry to add", true);
		publicCommands[5] = Commands.slash("secret", "Add a word to be a secret command").addOptions(new OptionData(OptionType.STRING, "word", "The word to become the command", true), new OptionData(OptionType.STRING, "response", "The response to the new command", true));
		publicCommands[6] = Commands.slash("unsecret", "Remove a word from secret commands").addOptions(new OptionData(OptionType.STRING, "word", "The word to remove from commands", true));
		
		// Update public commands
		if(!inIDE)
		{
			DMain.log("Updating slash commands");
			DMain.commands = jda.updateCommands().addCommands(publicCommands).complete();
		}
		
		BOT_NAME = jda.getSelfUser().getName();
		jda.getPresence().setPresence(Activity.of(ActivityType.WATCHING, "Democracy thrive"), false);
		
		BOT_ID = jda.getSelfUser().getIdLong();
		
		// Create InputListener
		CustomListener listener;
		
		try {
			listener = loadServerData(attempts);
		} catch(Throwable t) {
			DMain.error(t);
			DMain.sendToOperator("Something went wrong booting server. Bot is in recovery mode");
			listener = new GenericEventHandler(jda);
		}
		
		jda.addEventListener(listener);
		return listener;
	}
	
	public static ICommandReference getCommandByName(String commandName)
	{
		for(Command command : commands)
		{
			for(Subcommand subcommand : command.getSubcommands())
			{
				if(subcommand.getFullCommandName().equals(commandName))
				{
					return subcommand;
				}
			}
			
			if(command.getName().equals(commandName))
			{
				return command;
			}
		}
		
		Main.log.error("Failed to find command by name " + commandName);
		return null;
	}
	
	/**
	 * Reads MemberData
	 * @throws IllegalStateException
	 * @throws IOException 
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
		IMMIGRANT = guild.getRoleById(IMMIGRANT_ID);
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
		
		DMain.log("President: " + (president != null ? president.getEffectiveName() : "does not exist"));
		
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
			DMain.log("Using reset file");
			// Send the old file
			DMain.sendFileToOperator(serverFile);
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
	
	// Convenience methods for loggin
	public static void log(String info) { log.info(info); }
	
	/**
	 * Logs an error, but does not report it to owner.
	 * @param t
	 */
	public static void log(Throwable t)
	{
		StringBuilder error = new StringBuilder(t.toString() + "\n");
		
		for(int i = 0; i < t.getStackTrace().length; i++)
		{
			error.append("\tat " + t.getStackTrace()[i] + "\n");
		}
		
		log.error(error.toString());
	}
	
	public static void error(String error, Throwable t)
	{
		log.error(error, t);
		if(errorHandler != null) errorHandler.queue(error);
	}
	
	public static void error(String error)
	{
		log.error(error);
		if(errorHandler != null) errorHandler.queue(error);
	}
	
	public static void error(Throwable t)
	{
		DMain.error(t.getMessage(), t);
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
	
	/**
	 * Restarts the bot
	 */
	public static void reboot()
	{
		DMain.sendToOperator("Rebooting...");
		
		// Reboot
		try {
			Process p = Runtime.getRuntime().exec("sudo reboot");
			p.waitFor();
		} catch(InterruptedException | IOException e) {
			System.err.println("An error occured rebooting bot");
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException
	{
//		if(!inIDE)
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
						DMain.error(t.toString());
					}
				}
				
				@Override
				public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh)
				{
					com.supasulley.main.Main.log.error(eh.toString());
				}
			};
			
			runnable.setName("weeve");
			runnable.start();
		}
		
		new DMain();
	}
}
