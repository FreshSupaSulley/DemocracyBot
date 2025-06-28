package io.github.freshsupasulley.dbot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class PrivateHandler extends MessageHandler {
	
	public static final File JAR_FILE = new File(System.getProperty("user.home") + "/Desktop/DemocracyBot.jar");
	
	public static final String ENDPOINT = "https://api.start.gg/gql/alpha", OWNER_ID = "735931";
	private JDA jda;
	
	public PrivateHandler(JDA jda)
	{
		super(new String[] {"activity*Change what DBot is doing*(\"default\", \"competing\", \"listening\", \"streaming\", \"watching\", \"none\") + content", "enroll*Join the military for administrator access", "unenroll*Leave the military", "constitution*Change text in constitution", "channels*Get channel IDs", "say*Say a message in a channel*channel ID", "unbanAll*Unbans all members from Discordia", "role*Get role from ID", "edit*Edits a message*message ID + new text", "amendment*Force pass an amendment*text", "unamendment*Force remove an amendment*text", "ip*Retrieves local IP address", "update*Updates the bot from the latest release in the GitHub repo", "logs*Retrieve logs in a file and clears them", "data*Returns server data", "shutdown*Forces shutdown of " + Main.BOT_NAME, "reboot*Reboots " + Main.BOT_NAME, "clear*Clears 100 " + Main.BOT_NAME + " messages (bots cannot delete your private messages)",
		});
		
		this.jda = jda;
	}
	
	public String privateMessageReceived(int commandIndex, boolean isOwner, String message, PrivateChannel channel)
	{
		switch(commandIndex)
		{
			case (0):
				return handleActivityRequest(message);
			case (1):
				Guild guild = jda.getGuildById(Main.SERVER_ID);
				guild.addRoleToMember(guild.retrieveMemberById(Main.OWNER_ID).complete(), Main.THE_MILITARY).complete();
				return "Applied.";
			case (2):
				Guild guild2 = jda.getGuildById(Main.SERVER_ID);
				guild2.removeRoleFromMember(guild2.retrieveMemberById(Main.OWNER_ID).complete(), Main.THE_MILITARY).complete();
				return "Removed.";
			case (3):
				Guild guild3 = jda.getGuildById(Main.SERVER_ID);
				TextChannel constipation = guild3.getTextChannelById(Main.THE_CONSTIPATION);
				List<Message> messages = constipation.getHistory().retrievePast(1).complete();
				
				if(messages.size() == 1)
					messages.get(0).editMessage(message).complete();
				else
					constipation.sendMessage(message).queue();
				
				return "Done.";
			case (4):
				Guild guild4 = jda.getGuildById(Main.SERVER_ID);
				String result = "";
				for(TextChannel sample : guild4.getTextChannels())
				{
					result += sample.getName() + " - " + sample.getId();
				}
				return result;
			case (5):
				return handleSayRequest(message);
			case (6):
				Guild guild5 = jda.getGuildById(Main.SERVER_ID);
				String output = "";
				
				for(Ban ban : guild5.retrieveBanList().complete())
				{
					output += ban.getUser() + ", ";
					guild5.unban(ban.getUser());
				}
				
				if(output.isEmpty())
					return "No one is banned!";
				return output.substring(0, output.length() - 1);
			case (7):
				Role role = jda.getGuildById(Main.SERVER_ID).getRoleById(message);
				if(role == null)
					return "No role found";
				else
					return role.getName() + " " + role.getId();
			case (8):
				if(!message.contains(" "))
					return "Incorrect format";
				
				Guild guild6 = jda.getGuildById(Main.SERVER_ID);
				long id = 0;
				
				try
				{
					id = Long.parseLong(message.substring(0, message.indexOf(" ")));
				} catch(NumberFormatException e)
				{
					return "Not a valid ID";
				}
				
				for(TextChannel sample : guild6.getTextChannels())
				{
					try
					{
						sample.editMessageById(id, message.substring(message.indexOf(" ") + 1)).complete();
						return "Edited.";
					} catch(Exception e)
					{
						continue;
					}
				}
				return "Could not find message ID with ID " + id;
			case (9):
			{
				Main.server.addAmendment(jda, message);
				return "Passed " + message;
			}
			case (10):
			{
				int amendment;
				try
				{
					amendment = Integer.parseInt(message);
				} catch(Exception e)
				{
					return "Not a valid amendment #";
				}
				
				return Main.server.removeAmendment(jda, amendment);
			}
			case (11):
			{
				try(Socket socket = new Socket())
				{
					socket.connect(new InetSocketAddress("google.com", 80));
					return socket.getLocalAddress().getHostAddress();
				} catch(IOException e)
				{
					return "Could not access IP address: " + e.toString();
				}
			}
			case (12):
			{
				if(Main.inIDE)
					return "in IDE! Why are you tryna update prod from a dev env??";
				
				try
				{
					JsonObject json = new JsonObject();
					json.addProperty("ref", "main");
					return callGitHub("PUT", "/actions/workflows/update.yaml/dispatches", json.toString());
				} catch(IOException e)
				{
					Main.log.error("Failed to trigger update workflow", e);
					return e.getLocalizedMessage();
				}
			}
			case (13):
				return "refer to weeve";
			case (14):
				Main.updateServerData();
				Main.sendServerData();
				return null;
			case (15):
				Main.updateServerData();
				Main.shutdown();
				return null;
			case (16):
			{
				Main.updateServerData();
				reboot();
				return "Rebootin";
			}
			case (17):
				MessageHistory history = new MessageHistory(channel);
				List<Message> messages2 = history.retrievePast(100).complete();
				List<Message> toDelete = new ArrayList<Message>();
				
				for(int i = 0; i < messages2.size(); i++)
				{
					Message sample = messages2.get(i);
					if(sample.isPinned())
						continue;
					
					boolean isBot = sample.getAuthor().getIdLong() == Main.BOT_ID;
					
					if(isBot || getCommand(true, sample.getContentDisplay()) != null)
					{
						// Private channels can only delete bot messages
						if(isBot)
						{
							toDelete.add(messages2.get(i));
						}
					}
				}
				
				if(toDelete.size() > 1)
				{
					channel.purgeMessages(toDelete);
				}
				else if(toDelete.size() != 0)
				{
					toDelete.get(0).delete().queue();
				}
				return null;
			case (18):
				channel.sendMessageEmbeds(getUsage()).queue();
				return null;
			default:
				return null;
		}
	}
	
	/**
	 * Calls the GitHub API for bot updating purposes.
	 * 
	 * @param method HTTP method (e.g., GET, POST, PUT)
	 * @param path   API endpoint to hit (e.g., /actions/...), or absolute path (if it starts with http)
	 * @return responses
	 * @throws IOException if something goes wrong
	 */
	public static InputStream initGitHubRequest(String method, String path, @Nullable String body) throws IOException
	{
		URL url = new URL(path.startsWith("http") ? path : "https://api.github.com/repos/FreshSupaSulley/DemocracyBot" + path);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod(method);
		connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); // might as well stick to a fixed API version
		connection.setRequestProperty("Authorization", "Bearer " + Main.GITHUB_ACCESS_TOKEN);
		
		System.out.println(Main.GITHUB_ACCESS_TOKEN);
		
		if(body != null)
		{
			connection.setDoOutput(true);
			
			try(OutputStream os = connection.getOutputStream())
			{
				byte[] input = body.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			}
		}
		
		Main.log.info("Sending GH request to {}", url);
		return connection.getInputStream();
	}
	
	public static String callGitHub(String method, String path, @Nullable String body) throws IOException
	{
		String result = new String(initGitHubRequest(method, path, body).readAllBytes(), StandardCharsets.UTF_8);
		Main.log.info("Response from GH: {}", result);
		return result;
	}
	
	private String handleActivityRequest(String message)
	{
		if(!message.contains(" "))
		{
			if(message.equals("none"))
			{
				jda.getPresence().setActivity(null);
				return "Resetting activity...";
			}
			
			return commandErrors[0];
		}
		
		String activity = message.substring(0, message.indexOf(" "));
		
		for(ActivityType sample : ActivityType.values())
		{
			if(sample == ActivityType.CUSTOM_STATUS)
				continue;
			
			if(sample.toString().equalsIgnoreCase(activity))
			{
				if(sample == ActivityType.STREAMING)
				{
					if(message.indexOf(" ") == message.lastIndexOf(" "))
						return "Nah. Tell what you're watching, then provide a link.";
					jda.getPresence().setPresence(Activity.of(sample, message.substring(message.indexOf(" ") + 1, message.lastIndexOf(" ")), message.substring(message.lastIndexOf(" ") + 1)), false);
				}
				else
				{
					jda.getPresence().setPresence(Activity.of(sample, message.substring(message.indexOf(" ") + 1)), false);
				}
				
				return "Activity updated";
			}
		}
		
		return commandErrors[0];
	}
	
	/**
	 * Restarts the bot
	 */
	public static void reboot()
	{
		Main.updateServerData();
		
		// Reboot
		try
		{
			Process p = Runtime.getRuntime().exec("sudo reboot");
			p.waitFor();
		} catch(InterruptedException | IOException e)
		{
			System.err.println("An error occured rebooting bot");
			e.printStackTrace();
		}
	}
	
	private String handleSayRequest(String message)
	{
		long channelID = 0;
		String body = "";
		
		try
		{
			channelID = Long.parseLong(message.substring(0, message.indexOf(" ")));
			body = message.substring(message.indexOf(" ") + 1);
		} catch(IndexOutOfBoundsException | NumberFormatException e)
		{
			return commandErrors[4];
		}
		
		TextChannel channel = jda.getTextChannelById(channelID);
		if(channel == null)
			return "No channel found";
		if(!channel.getGuild().getSelfMember().hasAccess(channel))
			return "Can't speak in this channel!";
		
		channel.sendMessage(body).queue();
		
		return null;
	}
}
