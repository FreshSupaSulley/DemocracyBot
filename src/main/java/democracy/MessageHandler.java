package democracy;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Initializes public and private message listeners, and stores methods both listeners require
 */
public class MessageHandler {
	
	// Commands that contain ! at the beginning are secret commands
	protected String[] commands, commandDescriptions, commandErrors;
	protected InputListener listener;
	private MessageEmbed helpEmbed;
	
	/**
	 * Provide array of all commands and their usages. Each inCommandError in the array is bound to the corresponding String in the commands array.
	 * inCommandErrors.length() must equal inCommand.length().
	 */
	public MessageHandler(InputListener listener, String[] commands)
	{
		this.listener = listener;
		
		this.commands = new String[commands.length + 1];
		commandDescriptions = new String[commands.length + 1];
		commandErrors = new String[commands.length + 1];
		
		for(int i = 0; i < commands.length; i++)
		{
			String string = commands[i];
			
			int first = string.indexOf("*");
			int last = string.lastIndexOf("*");
			
			this.commands[i] = string.substring(0, first);
			
			// No error description
			if(first == last)
			{
				commandDescriptions[i] = string.substring(first + 1);
				commandErrors[i] = "";
			}
			else
			{
				commandDescriptions[i] = string.substring(first + 1, last);
				commandErrors[i] = string.substring(last + 1);
			}
		}
		
		// Add help commands
		this.commands[commands.length] = "help";
		commandDescriptions[commands.length] = "";
		commandErrors[commands.length] = "";
		
		// Create embeds for help command
		String result = "";
		
		for(int i = 0; i < this.commands.length - 1; i++)
		{
			String command = this.commands[i];
			result += "**" + DMain.DEFAULT_PREFIX + command + "**" + (commandErrors[i].length() > 0 ? " (`" + command + " " + "'" + commandErrors[i] + "'`)" : "") + (commandDescriptions[i].length() > 0 ? " -- " + commandDescriptions[i] : "") + "\n";
		}
		
		// Store standard + secret EmbedBuilders. Insert the command prefix when asked
		EmbedBuilder e = new EmbedBuilder();
		e.setTitle(DMain.BOT_NAME + " Usage");
		e.setColor(DMain.BURPLE);
		e.setDescription(result.substring(0, result.length() - 1) + "\n\n*All votes are found in <#" + DMain.VOTING_BOOTH + ">*");
		helpEmbed = e.build();
	}
	
	public int getCommandIndex(String command)
	{
		for(int i = 0; i < commands.length; i++)
		{
			String sample = commands[i];
			if(sample.charAt(0) == '!') sample = sample.substring(1);
			if(sample.equals(command)) return i;
		}
		
		System.err.println("Could not find command index from input " + command);
		return -1;
	}
	
	/**
	 * Returns the command in the message. If none exists, null is returned
	 */
	public String getCommand(boolean secret, String message)
	{
		for(String command : commands)
		{
			if(command.charAt(0) == '!')
			{
				if(!secret) continue;
				command = command.substring(1);
			}
			
			int space = message.indexOf(" ");
			if((DMain.DEFAULT_PREFIX + command).equals(message.substring(0, (space == -1 ? message.length() : space)))) return command;
		}
		
		return null;
	}
	
	/**
	 * Checks if the command needs to have a body. Null is returned if message is formatted correctly
	 */
	public String requiresBody(int commandIndex, String message)
	{
		if(commandErrors[commandIndex].isEmpty()) return null;
		
		if(!message.contains(" "))
		{
			String temp = commands[commandIndex];
			if(temp.startsWith("!")) temp = temp.substring(1);
			
			return temp + " '" + commandErrors[commandIndex] + "'";
		}
		
		return null;
	}
	
	public String[] getCommands()
	{
		return commands;
	}
	
	public MessageEmbed getUsage()
	{
		return helpEmbed;
	}
}
