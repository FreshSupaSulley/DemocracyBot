package democracy;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import democracy.Poll.PollType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.ResumedEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.utils.FileUpload;

public class InputListener extends ListenerAdapter {
	
	private PrivateHandler privateHandler;
	
	private JDA jda;
	private ArrayList<Poll> activePolls = new ArrayList<Poll>();
	private ArrayList<Candidate> candidates = new ArrayList<Candidate>();
	
	private Message presidentialVote, afterMessage;
	private long voteCreateTime, afterMessageTime;
	
	private final long presidentialVoteTime = 259200000;
	
	public InputListener(JDA jda)
	{
		this.jda = jda;
		privateHandler = new PrivateHandler(this, jda);
	}
	
	@Override
	public void onMessageReceived(MessageReceivedEvent event)
	{
		super.onMessageReceived(event);
		
		// Ignore if we cannot speak in the channel
		if(!event.getChannel().canTalk()) return;
		
		// Public commands
		if(event.getChannelType().isGuild())
		{
			guildMessageReceived(event);
		}
		// Private commands
		else if(event.isFromType(ChannelType.PRIVATE))
		{
			privateMessageReceived(event);
		}
	}
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
	{
		if(!event.isFromGuild())
		{
			event.reply("DM commands are not supported. Please use " + DMain.BOT_NAME + " in your server.").setEphemeral(true).queue();
			return;
		}
		
		// Message content
		User user = event.getUser();
		Guild guild = event.getGuild();
		
		ServerMember sender = getMember(user);
		
		DMain.log("[SLASH COMMAND (" + guild.getId() + ", channel #" + event.getChannel().getId() + ") - '" + user.getName() + "']: \"" + event.getCommandString() + "\"");
		
		switch(event.getCommandPath())
		{
			case "violation":
			{
				PollType type = PollType.VIOLATION;
				User mentioned = event.getOption("violator").getAsUser();
				
				if(mentioned.getIdLong() == DMain.BOT_ID)
				{
					event.reply("Go to hell.").queue();
					return;
				}
				
				// Check if we already have a poll open for this
				if(pollAlreadyOpen(type, mentioned.getIdLong()))
				{
					event.reply("Poll is already open!").queue();
				}
				else if(!sender.canPropose(type))
				{
					event.reply("You are forbidden from proposing timeouts this frequently.").queue();
				}
				else
				{
					event.reply("Poll added!").queue();
					
					OptionMapping option = event.getOption("minutes");
					int minutes;
					if(option != null) minutes = option.getAsInt();
					else minutes = 1;
					
					activePolls.add(new Poll(Color.red, type, "Timeout " + mentioned.getName(), "Do the people agree **" + mentioned.getName() + "** has violated our constitution / amendments? Violator will be timed out for " + minutes + " minute(s).", mentioned, jda.getTextChannelById(DMain.VOTING_BOOTH), () -> guild.timeoutFor(guild.retrieveMemberById(mentioned.getIdLong()).complete(), minutes, TimeUnit.MINUTES).complete()));
				}
				
				return;
			}
			case "impeach":
			{
				// Check if we have a president to overthrow
				if(!DMain.server.hasPresident())
				{
					event.reply("There is no President to impeach!").queue();
				}
				else if(pollAlreadyOpen(PollType.IMPEACH, 0))
				{
					event.reply("Poll is already open!").queue();
				}
				else if(presidentialVote != null)
				{
					event.reply("There is an active Presidential vote. If you are unhappy with the current President, vote to elect a new one.").queue();
				}
				else if(!sender.canPropose(PollType.IMPEACH))
				{
					event.reply("You cannot propose impeachment this frequently.").queue();
				}
				else
				{
					event.reply("Poll added!").queue();
					activePolls.add(new Poll(Color.red, PollType.IMPEACH, "Impeach the President", "Has our President violated the Magna Farta? Do we, the people, decide to impeach <@" + DMain.server.getPresidentID() + ">?", null, jda.getTextChannelById(DMain.VOTING_BOOTH), () -> DMain.server.impeachPresident(guild)));
				}
				
				return;
			}
			case "campaign":
			{
				String content = event.getOption("slogan").getAsString();
				
				if(DMain.server.isPresident(sender))
				{
					if(DMain.server.isLastTerm())
					{
						event.reply("You cannot be elected President for more than two terms at a time.").queue();
						return;
					}
					else
					{
						event.reply("The President automatically runs for re-election once voting begins.").queue();
						return;
					}
				}
				
				// You can only campaign when the presidential vote poll is live
				if(presidentialVote == null)
				{
					float daysRemaining = DMain.server.millsRemainingInTerm() / 8.64e+7f;
					event.reply("The Presidential Election is active during the last 3 days of the President's term (last day is " + new SimpleDateFormat("MM/dd/yyyy").format(new Date(System.currentTimeMillis() + DMain.server.millsRemainingInTerm())) + "). The President has " + (int) (daysRemaining) + " day" + ((int) daysRemaining != 1 ? "s" : "") + " and " + (int) (daysRemaining % 1 * 24) + " hours left in office. You will be notified in <#" + DMain.VOTING_BOOTH + "> when the election begins.").queue();
				}
				else if(candidates.size() == 10)
				{
					event.reply("There are too many candidates running for office. Only 10 at a time.").queue();
				}
				else if(content.length() > 200)
				{
					event.reply("Slogan must be less than 200 characters.").queue();
				}
				else
				{
					// Check if already campaigning
					for(Candidate candidate : candidates)
					{
						if(candidate.getID() == sender.getID())
						{
							event.reply("You're already campaigning!").queue();
							return;
						}
					}
					
					Role party = event.getOption("party").getAsRole();
					candidates.add(new Candidate(user.getIdLong(), content, party));
					
					// Add reactions
					for(int i = 0; i < candidates.size(); i++)
					{
						if(i != 9)
						{
							presidentialVote.addReaction(Emoji.fromUnicode("U+3" + (i + 1) + "U+fe0fU+20e3")).queue();
						}
						else
						{
							presidentialVote.addReaction(Emoji.fromUnicode("U+1f51f")).queue();
						}
					}
					
					presidentialVote.editMessageEmbeds(buildPresidentialVote()).complete();
					event.reply("You are now campaigning! Check the Presidential Voting poll in <#" + DMain.VOTING_BOOTH + ">.").queue();
				}
				
				return;
			}
			case "next-election":
			{
				event.reply("The next election opens on **" + getExactTime(System.currentTimeMillis() + DMain.server.millsRemainingInTerm() - presidentialVoteTime) + " EST**.").queue();
				return;
			}
			case "propose":
			{
				String content = event.getOption("amendment").getAsString();
				
				if(!sender.canPropose(PollType.PROPOSE))
				{
					event.reply("You cannot propose amendments this frequently.").queue();
				}
				else
				{
					event.reply("Poll added!").queue();
					activePolls.add(new Poll(DMain.BURPLE, PollType.PROPOSE, "Propose \"" + content + "\"", "Do the people agree to append the proposed legislation in <#" + DMain.AMENDMENTS + ">:\n\n\"" + content + "\"\n\nThe President is expected to enforce this new legislation.", null, jda.getTextChannelById(DMain.VOTING_BOOTH), DMain.server.addAmendment(jda, content)));
					break;
				}
				
				return;
			}
			case "repeal":
			{
				int number = event.getOption("amendment-number").getAsInt();
				
				if(number < 1 || number > DMain.server.getAmendments())
				{
					event.reply("Enter a number between 1-" + DMain.server.getAmendments()).queue();
					return;
				}
				
				if(!sender.canPropose(PollType.REPEAL))
				{
					event.reply("You cannot repeal amendments this frequently.").queue();
				}
				else
				{
					number--;
					event.reply("Poll added!").queue();
					activePolls.add(new Poll(DMain.BURPLE, PollType.REPEAL, "Repeal \"" + DMain.server.getAmendment(jda, number) + "\"", "Do the people agree to repeal \"" + DMain.server.getAmendment(jda, number) + "\" from <#" + DMain.AMENDMENTS + ">?", null, jda.getTextChannelById(DMain.VOTING_BOOTH), DMain.server.repealAmendment(jda, number)));
				}
				
				return;
			}
			// Unused
//			case "party/create":
//			{
//				// Vote to create a new party
//				String name = event.getOption("name").getAsString();
//				
//				if(!sender.canPropose(PollType.CREATE_PARTY))
//				{
//					event.reply("You cannot create parties this frequently.").queue();
//				}
//				else
//				{
//					event.reply("Poll added!").queue();
//					activePolls.add(new Poll(DMain.BURPLE, PollType.CREATE_PARTY, "Create political party \"" + name + "\"", "Do the people agree to establish a new political party titled \"" + name + "\"?", null, jda.getTextChannelById(DMain.VOTING_BOOTH), () -> DMain.VOTER.createCopy().setColor((int) (Math.random() * Math.pow(2, 24))).setName(name).complete()));
//				}
//				
//				return;
//			}
//			case "party/join":
//			{
//				// Check for forbidden roles
//				Role role = event.getOption("party").getAsRole();
//				
//				if(role.getPermissionsRaw() != DMain.VOTER.getPermissionsRaw())
//				{
//					event.reply("This role isn't a political party!").queue();
//				}
//				else
//				{
//					// If user already has the role
//					if(!event.getMember().getRoles().contains(role))
//					{
//						event.reply("You are already a member of this political party.").queue();
//					}
//					else
//					{
//						guild.addRoleToMember(user, role).queue();
//						event.reply("Joined political party **" + role.getName() + "**!").queue();
//					}
//				}
//				
//				return;
//			}
//			case "party/leave":
//			{
//				Role role = event.getOption("party").getAsRole();
//				
//				// If trying to leave a forbidden role
//				if(role.getPermissionsRaw() != DMain.VOTER.getPermissionsRaw())
//				{
//					event.reply("This role isn't a political party!").queue();
//				}
//				else
//				{
//					// If user doesn't have the role
//					if(!event.getMember().getRoles().contains(role))
//					{
//						event.reply("You aren't a member of this political party.").queue();
//					}
//					else
//					{
//						guild.removeRoleFromMember(user, role).complete();
//						event.reply("Left political party **" + role.getName() + "**").queue();
//					}
//				}
//				
//				return;
//			}
			case "archive":
			{
				String entry = event.getOption("entry").getAsString();
				
				if(!sender.canPropose(PollType.ARCHIVE))
				{
					event.reply("You cannot propose an entry to the Library of Congress this frequently.").queue();
				}
				else
				{
					event.reply("Poll added!").queue();
					activePolls.add(new Poll(DMain.BURPLE, PollType.ARCHIVE, "Archive \"" + entry + "\"", "Do the people agree to archive the following text in <#" + DMain.LIBRARY_OF_CONGRESS + ">:\n\n\"" + entry + "\"\n\nThe President also has the power to archive by force.", null, jda.getTextChannelById(DMain.VOTING_BOOTH), jda.getTextChannelById(DMain.LIBRARY_OF_CONGRESS).sendMessage(entry)::complete));
					break;
				}
				
				return;
			}
			case "secret":
			{
				String word = event.getOption("word").getAsString().trim();
				String response = event.getOption("response").getAsString().trim();
				String response2 = event.getOption("response-2", "", option -> "\n" + option.getAsString().trim());
				String response3 = event.getOption("response-3", "", option -> "\n" + option.getAsString().trim());
				
				DMain.server.addSecret(word, response + response2 + response3);
				
				event.reply("\"" + word + "\" is now a secret command (will send " + response + ")").queue();
				return;
			}
			case "unsecret":
			{
				String word = event.getOption("word").getAsString();
				
				// If success
				if(DMain.server.removeSecret(word))
				{
					event.reply("Removed \"" + word + "\" from secret commands").queue();
				}
				else
				{
					event.reply("\"" + word + "\" isn't a secret command").queue();
				}
				
				return;
			}
			default:
			{
				System.err.println("Unhandled command " + event.getName());
				event.getHook().sendMessage("Tell Sulley something is wrong with the bot (which of course, there isn't)").queue();
				return;
			}
		}
	}
	
	private MessageEmbed buildPresidentialVote()
	{
		EmbedBuilder e = new EmbedBuilder();
		e.setTitle(new SimpleDateFormat("MM/dd/yyyy").format(new Date()) + " Presidential Election");
		e.setColor(Color.red);
		
		String description = "@everyone it's time. By the power of the people and the Magna Farta, we will elect our next monthly President that represents the core of this nation's beliefs and thereby representing the people. Cast your vote below:\n";
		int index = 0;
		
		for(Candidate candidate : candidates)
		{
			description += "\n**#" + (++index) + ": <@" + candidate.getID() + ">** (<@&" + candidate.getRole().getId() + ">) - *\"" + candidate.getSlogan() + "\"*";
		}
		
		if(candidates.isEmpty()) description += "\n*There are no active presidential candidates. Run for office with /campaign.*";
		
		e.setImage("https://cdn.discordapp.com/app-icons/910579031391498330/c65afb3995baa1c31212e43f1f643e7e.png");
		e.setDescription(description);
		e.setFooter("Vote will be decided in " + (int) (presidentialVoteTime / 3.6e+6) + " hours. Thank you for being an active participant in our perfect society.");
		return e.build();
	}
	
	/**
	 * Check if we already have a poll open for this
	 * @param type
	 * @param pollFocusID
	 * @return
	 */
	private boolean pollAlreadyOpen(PollType type, long pollFocusID)
	{
		for(Poll poll : activePolls)
		{
			// If poll of the same type already active
			if(poll.getType() == type)
			{
				switch(type)
				{
					case VIOLATION:
						if(poll.getFocusMemberID() == pollFocusID) return true;
						break;
					case IMPEACH:
						return true;
					default:
						break;
				}
			}
		}
		
		return false;
	}
	
	public void guildMessageReceived(MessageReceivedEvent event)
	{
		if(event.getAuthor().isBot() || DMain.SERVER_ID != event.getGuild().getIdLong()) return;
		
		// Get user
		TextChannel channel = event.getChannel().asTextChannel();
		
		// Handle server command requests
		String message = event.getMessage().getContentDisplay().replaceAll("\n", "");
		
		// Secret commands
		if(channel.getParentCategory() == null || channel.getParentCategory().getIdLong() == DMain.THE_WHITE_HOUSE_CATEGORY || channel.getParentCategory().getIdLong() == DMain.MAGNA_FARTA_CATEGORY) return;
		if(message.contains("http")) return;
		
//		boolean canReact = eventMessage.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_ADD_REACTION);
//		int numReacts = 0;
		StringBuilder builder = new StringBuilder();
		
		// For each key (word)
		for(String key : DMain.server.getSecretCommands().keySet())
		{
			// If it CONTAINS, not equals
			if(message.toLowerCase().contains(key.toLowerCase()))
			{
				String[] values = DMain.server.getSecretCommands().get(key).split("\n");
				// Pick random link
				String value = values[(int) (Math.random() * values.length)];
				
				// +1 for break character
				if(builder.length() + value.length() + 1 > Message.MAX_CONTENT_LENGTH)
					break;
				
				// Append command
				builder.append(value + "\n");
//				
//				if(canReact && numReacts < Message.MAX_REACTIONS)
//				{
//					numReacts++;
//					eventMessage.addReaction(Emoji.fromUnicode(unicode)).queue();
//				}
			}
		}
		
		sendMessage(event.getChannel(), builder.toString());
		
		// Discord mod secret command
		if(event.getChannel().getName().contains("general"))
		{
			if(message.toLowerCase().contains("meme"))
			{
				sendMessage(event.getChannel(), "https://tenor.com/view/discord-mod-discord-gif-18242678");
			}
		}
	}
	
	/**
	 * Private messages do not record anything other than logs
	 */
	public void privateMessageReceived(MessageReceivedEvent event)
	{
		if(event.getAuthor().getIdLong() != DMain.OWNER_ID) return;
		
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
	 * @param channel
	 * @param message
	 */
	public static void sendMessage(MessageChannel channel, String message)
	{
		try {
			if(message == null) return;
			
			if(message.isEmpty())
			{
				return;
			}
			if(message.length() > Message.MAX_CONTENT_LENGTH)
			{
				if(channel instanceof PrivateChannel)
				{
					PrivateChannel privateChannel = (PrivateChannel) channel;
					
					if(privateChannel.getUser().getIdLong() != DMain.OWNER_ID)
					{
						System.err.println("[ERROR] Sending large message to a user that's not you!");
					}
					
					try {
						File temp = File.createTempFile("temp", ".txt");
						
						FileWriter writer = new FileWriter(temp);
						writer.append(message);
						writer.close();
						
						privateChannel.sendFiles(FileUpload.fromData(temp)).queue();
						
						if(!temp.delete()) System.err.println("Could not delete temp file");
					} catch(IOException e) {
						DMain.log(e);
					}
				}
				else
				{
					System.err.println("[ERROR] Over " + Message.MAX_CONTENT_LENGTH + " characters being sent to " + channel.getClass() + "! Shortening...");
					message = "[**INTERNAL ERROR**] " + DMain.BOT_NAME + " tried to send an extremely large message. Developers have been notified.";
				}
				
				return;
			}
			
			DMain.log("[SENDING IN CHANNEL " + channel.getName() + "]: \"" + message.substring(0, Math.min(message.length(), 40)).replace("\n", "") + "\"...");
			channel.sendMessage(message).queue();
		} catch(Exception e) {
			System.err.println("Something went wrong sending message " + message + ". Discord issue? Check https://discordstatus.com/");
			DMain.log(e);
		}
	}
	
	public ServerMember getMember(User user)
	{
		// Now that we have the server, search for member within server
		for(ServerMember member : DMain.server.getMembers())
		{
			// If the user already exists, move to front of list
			if(member.isUser(user.getIdLong()))
			{
				member.update(user.getName(), user.getDiscriminator());
				return member;
			}
		}
		
		// If we couldn't find user / server, the ServerMember is new
		ServerMember initMember = new ServerMember(user.getName(), user.getDiscriminator(), user.getIdLong());
		DMain.server.addMember(initMember);
		return initMember;
	}
	
	/**
	 * Should be called with long intervals (i.e. 30 seconds min)
	 */
	public void tick()
	{
		// Handle active polls
		Iterator<Poll> iterator = activePolls.iterator();
		
		while(iterator.hasNext())
		{
			Poll poll = iterator.next();
			
			if(poll.isComplete())
			{
				DMain.log("Poll " + poll.getType() + " is complete! Removing...");
				iterator.remove();
				DMain.updateServerData();
			}
		}
		
		DMain.server.tick(jda);
		
		// If we're voting for President
		if(presidentialVote != null || DMain.server.millsRemainingInTerm() < presidentialVoteTime)
		{
			// If a poll needs to be created
			if(presidentialVote == null)
			{
				DMain.log("Opening up Presidential vote");
				voteCreateTime = System.currentTimeMillis();
				
				// Add President as a re-election
				if(DMain.server.hasPresident() && !DMain.server.isLastTerm())
				{
					candidates.add(new Candidate(DMain.server.getPresidentID(), DMain.server.getPresidentialSlogan(), DMain.THE_PRESIDENT));
				}
				
				// Create vote, add first reaction (President re-election)
				presidentialVote = jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).sendMessageEmbeds(buildPresidentialVote()).complete();
				if(DMain.server.hasPresident()) presidentialVote.addReaction(Emoji.fromUnicode("U+31U+fe0fU+20e3")).queue();
			}
			// Tick vote if already created
			else
			{
				// Decide vote if over a day
				if(System.currentTimeMillis() - voteCreateTime > presidentialVoteTime)
				{
					if(!candidates.isEmpty())
					{
						// Tally votes
						int[] votes = new int[10];
						
						// Update message to get reactions
						presidentialVote = jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).retrieveMessageById(presidentialVote.getId()).complete();
						
						for(MessageReaction r : presidentialVote.getReactions())
						{
							EmojiUnion emoji = r.getEmoji();
							if(emoji.getType() == Emoji.Type.CUSTOM) continue;
							
							switch(emoji.asUnicode().getAsCodepoints())
							{
								case "U+31U+fe0fU+20e3":
									votes[0] = r.getCount();
									break;
								case "U+32U+fe0fU+20e3":
									votes[1] = r.getCount();
									break;
								case "U+33U+fe0fU+20e3":
									votes[2] = r.getCount();
									break;
								case "U+34U+fe0fU+20e3":
									votes[3] = r.getCount();
									break;
								case "U+35U+fe0fU+20e3":
									votes[4] = r.getCount();
									break;
								case "U+36U+fe0fU+20e3":
									votes[5] = r.getCount();
									break;
								case "U+37U+fe0fU+20e3":
									votes[6] = r.getCount();
									break;
								case "U+38U+fe0fU+20e3":
									votes[7] = r.getCount();
									break;
								case "U+39U+fe0fU+20e3":
									votes[8] = r.getCount();
									break;
								case "U+1f51f":
									votes[9] = r.getCount();
									break;
							}
						}
						
						Candidate nextPreident = candidates.get(0);
						int maxVotes = votes[0];
						
						DMain.log("Counting presidential votes! Candidate 1 = " + nextPreident.getID() + " " + maxVotes);
						
						for(int i = 1; i < candidates.size(); i++)
						{
							Candidate candidate = candidates.get(i);
							DMain.log(candidate.getID() + " " + votes[i]);
							
							if(votes[i] > maxVotes)
							{
								nextPreident = candidate;
								maxVotes = votes[i];
							}
						}
						
						candidates.clear();
						
						// President is elected
						DMain.log(nextPreident.getID() + " won");
						Guild guild = jda.getGuildById(DMain.SERVER_ID);
						
						// Remove President roll
						if(DMain.server.hasPresident())
						{
							guild.removeRoleFromMember(guild.retrieveMemberById(DMain.server.getPresidentID()).complete(), DMain.THE_PRESIDENT).complete();
						}
						
						// Delete Presidential vote
						presidentialVote.delete().queue();
						presidentialVote = null;
						
						// Transfer power
						DMain.server.electPresident(nextPreident);
						afterMessage = guild.getTextChannelById(DMain.VOTING_BOOTH).sendMessage("Welcome <@" + nextPreident.getID() + "> to The White House!").complete();
						afterMessageTime = System.currentTimeMillis();
						guild.addRoleToMember(guild.retrieveMemberById(nextPreident.getID()).complete(), DMain.THE_PRESIDENT).complete();
						
						// Update data
						DMain.updateServerData();
					}
					else
					{
						// Push vote time back a day
						voteCreateTime = System.currentTimeMillis();
					}
				}
			}
		}
		
		// Delete after message
		if(afterMessage != null && System.currentTimeMillis() - afterMessageTime > 3.6e+6)
		{
			afterMessage.delete().queue();
			afterMessage = null;
		}
	}
	
	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event)
	{
		super.onGuildMemberJoin(event);
		
		// Add into immigration
		event.getGuild().addRoleToMember(event.getMember(), DMain.IMMIGRANT).queue();
		DMain.server.addImmigrant(event.getMember().getIdLong());
		
		DMain.log(event.getUser().getAsTag() + " joined!");
	}
	
	@Override
	public void onGuildMemberRemove(GuildMemberRemoveEvent event)
	{
		super.onGuildMemberRemove(event);
		
		User user = event.getUser();
		Iterator<ServerMember> iterator = DMain.server.getMembers().iterator();
		
		while(iterator.hasNext())
		{
			ServerMember member = iterator.next();
			
			if(member.getID() == user.getIdLong())
			{
				DMain.sendToOperator(member.getName() + " left the server!");
				
				DMain.server.removeMember(member.getID());
				removeMember(member.getID());
				iterator.remove();
				
				return;
			}
		}
		
		DMain.sendToOperator("Unlogged participant left the server? " + user.getName());
	}
	
	/**
	 * Remove this member from nomination
	 */
	public void removeMember(long id)
	{
		// If this was the president
		if(DMain.server.hasPresident() && id == DMain.server.getPresidentID())
		{
			DMain.sendToOperator("The President left the running!");
			
			try {
				DMain.server.impeachPresident(jda.getGuildById(DMain.SERVER_ID)).call();
			} catch(Exception e) {
				DMain.log(e);
			}
		}
		
		// Remove member from candidates
		Iterator<Candidate> iterator = candidates.iterator();
		
		while(iterator.hasNext())
		{
			Candidate member = iterator.next();
			
			if(member.getID() == id)
			{
				DMain.log("Removing candidate from running");
				iterator.remove();
				
				presidentialVote.clearReactions().complete();
				
				// Add reactions
				for(int i = 0; i < candidates.size(); i++)
				{
					if(i != 9)
					{
						presidentialVote.addReaction(Emoji.fromUnicode("U+3" + (i + 1) + "U+fe0fU+20e3")).queue();
					}
					else
					{
						presidentialVote.addReaction(Emoji.fromUnicode("U+1f51f")).queue();
					}
				}
				
				presidentialVote.editMessageEmbeds(buildPresidentialVote()).complete();
				
				// Warn of candidate leave
				jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).sendMessage("@everyone Presidential candidate <@" + member + "> has left the server, offsetting all votes. This has forced a recount. The decision date has been postponed a day.").queue();
				voteCreateTime = System.currentTimeMillis();
				
				return;
			}
		}
	}
	
	@Override
	public void onDisconnect(DisconnectEvent event)
	{
		super.onDisconnect(event);
		CloseCode code = event.getCloseCode();
		DMain.log("DISCONNECTED! Close code: " + (code == null ? "null" : code.getCode() + ". Meaning: " + code.getMeaning()) + ". Closed by discord: " + event.isClosedByServer());
	}
	
	@Override
	public void onResumed(ResumedEvent event)
	{
		super.onResumed(event);
		DMain.log("Reconnected!");
	}
	
	@Override
	public void onException(ExceptionEvent event)
	{
		DMain.log("JDA Exception! Response code: " + event.getResponseNumber() + ". Logged: " + event.isLogged() + ". Cause: " + event.getCause());
	}
	
	private String getExactTime(long time)
	{
		return new SimpleDateFormat("MM/dd/yyyy h:mm aa").format(new Date(time));
	}
}
