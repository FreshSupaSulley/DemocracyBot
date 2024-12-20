package democracy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;

public class GenericEventHandler extends CustomListener {
	
	private PrivateHandler privateHandler;
	protected JDA jda;
	
	public GenericEventHandler(JDA jda)
	{
		privateHandler = new PrivateHandler(this.jda = jda);
//		jda.getGuildById(DMain.SERVER_ID).getTextChannelById("1102051068969504768").getHistoryFromBeginning(100).queue(history -> {
//			history.getRetrievedHistory().forEach(message -> {
//				// Check for polls
//				checkMessageForPoll(message);
//			});
//		}, DMain::error);
	}
	
	/**
	 * Private messages do not record anything other than logs
	 */
	public void onPrivateMessageReceived(MessageReceivedEvent event)
	{
		if(event.getAuthor().getIdLong() != DMain.OWNER_ID)
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
			sendMessage(event.getChannel(), "`" + DMain.DEFAULT_PREFIX + check + "`");
			return;
		}
		
		sendMessage(event.getChannel(), privateHandler.privateMessageReceived(commandIndex, true, message.substring(DMain.DEFAULT_PREFIX.length() + command.length() + (message.contains(" ") ? 1 : 0)), (PrivateChannel) event.getChannel()));
	}
	
	/**
	 * Queues a message to be sent in a MessageChannel. Works for both public and private channels
	 * 
	 * @param channel
	 * @param message
	 */
	public static void sendMessage(MessageChannel channel, String message)
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
					
					if(privateChannel.getUser().getIdLong() != DMain.OWNER_ID)
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
						DMain.log.error("Failed to do file shit", e);
					}
				}
				else
				{
					System.err.println("[ERROR] Over " + Message.MAX_CONTENT_LENGTH + " characters being sent to " + channel.getClass() + "! Shortening...");
					message = "[**INTERNAL ERROR**] " + DMain.BOT_NAME + " tried to send an extremely large message. Developers have been notified.";
				}
				
				return;
			}
			
			DMain.log.info("[SENDING IN CHANNEL {}]: \"{}\"...", channel.getName(), message.substring(0, Math.min(message.length(), 40)).replace("\n", ""));
			channel.sendMessage(message).queue();
		} catch(Exception e)
		{
			DMain.log.error("Something went wrong sending message " + message + ". Discord issue? Check https://discordstatus.com/", e);
		}
	}
	
	// Unused in recovery mode
	public void onGuildMessageReceived(MessageReceivedEvent event)
	{
	}
	
	public void tick()
	{
	}
}
