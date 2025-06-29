package io.github.freshsupasulley.dbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;

public class GenericEventHandler extends CustomListener {
	
	private Map<Long, Consumer<ButtonInteractionEvent>> buttonActionMap;
	private PrivateHandler privateHandler;
	
	public GenericEventHandler(JDA jda)
	{
		privateHandler = new PrivateHandler(jda);
		buttonActionMap = new HashMap<Long, Consumer<ButtonInteractionEvent>>();
	}
	
	/**
	 * Return a simple response when subclasses do not implement this.
	 */
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
	{
		event.reply(Main.BOT_NAME + " is booting :robot:").queue();
	}
	
	/**
	 * Adds an action to be fired when a button is pressed on a message.
	 * 
	 * @param message  message object. Can be accessed when {@link MessageChannel#sendMessage(CharSequence)} succeeds
	 * @param consumer action to be performed if successful
	 */
	public final void addButtonAction(Message message, Consumer<ButtonInteractionEvent> consumer)
	{
		buttonActionMap.put(message.getIdLong(), consumer);
	}
	
	@Override
	public final void onButtonInteraction(ButtonInteractionEvent event)
	{
		long id = event.getMessageIdLong();
		
		// If there's a button mapping associated with this id
		if(buttonActionMap.containsKey(id))
		{
			buttonActionMap.remove(id).accept(event);
		}
		else
		{
			Main.log.error("No button mapping associated for message {}", id);
			event.reply("This event has expired").setEphemeral(true).queue();
		}
	}
	
	// Used to update the bot
	@Override
	public final void onGuildMessageReceived(MessageReceivedEvent event)
	{
		// Only consider the webhook build successes
		if(event.getChannel().getIdLong() != Main.GITHUB || event.getAuthor().getIdLong() != Main.GITHUB_WEBHOOK_ID)
			return;
		
		// We have the priviledged intent for this
		try
		{
			String actionID = event.getMessage().getContentRaw();
			
			// Get the artifact associated with the run ID
			JsonArray array = JsonParser.parseString(PrivateHandler.callGitHub("GET", "/actions/runs/" + actionID + "/artifacts", null)).getAsJsonObject().get("artifacts").getAsJsonArray();
			if(array.size() != 1)
				throw new IllegalStateException("Artifact size != 1!");
			
			String id = array.get(0).getAsJsonObject().get("id").getAsString();
			Main.log.info("Downloading artifact with ID: {}", id);
			
			try(InputStream stream = PrivateHandler.initGitHubRequest("GET", "https://api.github.com/repos/FreshSupaSulley/DemocracyBot/actions/artifacts/" + id + "/zip", null))
			{
				// Temp zip, then we extract to jar
				File zipFile = File.createTempFile("temp", "zip");
				
				Main.log.info("Downloading to " + zipFile.getAbsolutePath());
				Files.copy(stream, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				
				// Done downloading!
				// Unzip and extract .jar
				try(ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile)))
				{
					ZipEntry entry;
					
					while((entry = zis.getNextEntry()) != null)
					{
						if(entry.getName().endsWith(".jar"))
						{
							Main.log.info("Extracting JAR: {}", entry.getName());
							Files.copy(zis, PrivateHandler.JAR_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
							break;
						}
					}
				}
				
				zipFile.delete();
				
				// Do some clean up
				event.getMessage().delete().complete(); // delete the webhook message
				PrivateHandler.callGitHub("DELETE", "/actions/runs/" + actionID, null); // delete the run to reduce clogging
				
				Main.sendToOperator("Done updating! Rebooting...");
				PrivateHandler.reboot();
			}
		} catch(Exception e)
		{
			// This should send to operator
			Main.log.error("Failed to update jar", e);
		}
	}
	
	/**
	 * Private messages do not record anything other than logs
	 */
	@Override
	public void onPrivateMessageReceived(MessageReceivedEvent event)
	{
		if(event.getAuthor().getIdLong() != Main.OWNER_ID)
			return;
		
		String message = event.getMessage().getContentDisplay();
		String command = "";
		
		command = privateHandler.getCommand(true, message);
		
		if(command == null)
			return;
		
		int commandIndex = privateHandler.getCommandIndex(command);
		String check = privateHandler.requiresBody(commandIndex, message);
		
		if(check != null)
		{
			sendMessage(event.getChannel(), "`" + Main.DEFAULT_PREFIX + check + "`");
			return;
		}
		
		sendMessage(event.getChannel(), privateHandler.privateMessageReceived(commandIndex, true, message.substring(Main.DEFAULT_PREFIX.length() + command.length() + (message.contains(" ") ? 1 : 0)), (PrivateChannel) event.getChannel()));
	}
	
	/**
	 * Queues a message to be sent in a MessageChannel. Works for both public and private channels
	 * 
	 * @param channel
	 * @param message
	 */
	@Deprecated
	private static void sendMessage(MessageChannel channel, String message)
	{
		try
		{
			if(message == null || message.isEmpty())
				return;
			
			if(message.length() > Message.MAX_CONTENT_LENGTH)
			{
				if(channel instanceof PrivateChannel)
				{
					PrivateChannel privateChannel = (PrivateChannel) channel;
					
					if(privateChannel.getUser().getIdLong() != Main.OWNER_ID)
					{
						System.err.println("[ERROR] Sending large message to a user that's not you!");
					}
					
					try
					{
						File temp = File.createTempFile("temp", ".txt");
						
						FileWriter writer = new FileWriter(temp);
						writer.append(message);
						writer.close();
						
						privateChannel.sendFiles(FileUpload.fromData(temp)).queue();
						
						if(!temp.delete())
							System.err.println("Could not delete temp file");
					} catch(IOException e)
					{
						Main.log.error("Failed to do file shit", e);
					}
				}
				else
				{
					System.err.println("[ERROR] Over " + Message.MAX_CONTENT_LENGTH + " characters being sent to " + channel.getClass() + "! Shortening...");
					message = "[**INTERNAL ERROR**] " + Main.BOT_NAME + " tried to send an extremely large message. Developers have been notified.";
				}
				
				return;
			}
			
			Main.log.info("[SENDING IN CHANNEL {}]: \"{}\"...", channel.getName(), message.substring(0, Math.min(message.length(), 40)).replace("\n", ""));
			channel.sendMessage(message).queue();
		} catch(Exception e)
		{
			Main.log.error("Something went wrong sending message " + message + ". Discord issue? Check https://discordstatus.com/", e);
		}
	}
}
