package democracy;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.supasulley.utils.JsonUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class Server {
	
	/** Term length is 30 days */
	public static final long TERM_LENGTH = 2592000000L;
	/** Presidential vote time is 3 days */
	public static final long PRESIDENTIAL_VOTE_TIME = 259200000;
	/** Update CAQ once per day */
	private static final long CAQ_UPDATE_TIME = 86400000;
	
	// Everything has to be initialized in case it doesn't get deserialized and a new server obj is created
	// Serialize
	private int presidentialCount = 0;
	private List<String> amendmentIDs = new ArrayList<String>();
	private Map<String, String> caqEntries = new HashMap<String, String>();
	private List<Poll> polls = new ArrayList<Poll>();
	private List<ServerMember> memberCache = new ArrayList<ServerMember>();
	private long presidentID;
	private String slogan;
	private long termEndTime;
	
	@SuppressWarnings("unused")
	private boolean lastTerm;
	
	// Presidential vote
	// Try to store these. Start of program should correct them if it can't be found
	private long presidentialVoteMessageID;
	private ArrayList<Candidate> candidates = new ArrayList<Candidate>();
	
	// Serialize last because its hella annoying
	private Map<String, String> secretCommands = new HashMap<String, String>(),
										unsecretedCommands = new HashMap<String, String>();
	
	// Don't serialize
	private transient Message presidentialVote;
	private transient long lastCAQ = System.currentTimeMillis();
	
	public Message getPresidentialVote(JDA jda)
	{
		if(presidentialVoteMessageID == 0)
			return null;
		
		// If in cache
		if(presidentialVote != null)
			return presidentialVote;
		
		// Otherwise retrieve it
		return jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).retrieveMessageById(presidentialVoteMessageID).onErrorMap(error ->
		{
			DMain.log.error("Huh? Tried to fetch presidential vote but fail checks failed", error);
			return null;
		}).onSuccess(message ->
		{
			this.presidentialVote = message;
		}).complete();
	}
	
	public int getPresidentialCount()
	{
		return presidentialCount;
	}
	
	public boolean isPresident(ServerMember member)
	{
		if(!hasPresident())
			return false;
		if(member.getID() == presidentID)
			return true;
		return false;
	}
	
	public ServerMember getMember(User user)
	{
		// Now that we have the server, search for member within server
		for(ServerMember member : memberCache)
		{
			// If the user already exists, move to front of list
			if(member.getID() == user.getIdLong())
			{
				return member;
			}
		}
		
		// If we couldn't find user / server, the ServerMember is new
		ServerMember initMember = new ServerMember(user.getIdLong());
		memberCache.add(initMember);
		DMain.updateServerData();
		return initMember;
	}
	
	/**
	 * Starts a poll if another doesn't already exist.
	 * 
	 * @param poll poll to start
	 * @return user response, indicating if it the request was successful
	 */
	public void beginPoll(SlashCommandInteractionEvent event, Poll poll)
	{
		// Ensure a duplicate poll doesn't exist
		for(Poll sample : polls)
		{
			if(poll.isDuplicate(sample))
			{
				event.reply("Another poll of this kind already exists!").setEphemeral(true).queue();
				return;
			}
		}
		
		// Ensure no one is spamming the poll
		ServerMember member = getMember(event.getUser());
		
		// This HAS to be the last thing checked, as it will add a cooldown to the command
		if(!member.canPropose(poll))
		{
			float millisLeft = (int) (member.getMillisRemaining(poll) / 3600000F * 100) / 100F;
			event.reply("You cannot " + DMain.getCommandReference(event.getFullCommandName()) + " this frequently (" + (millisLeft < 0.1f ? "< 0.1" : millisLeft) + "hr cooldown)").setEphemeral(true).queue();
			return;
		}
		
		// Only update the server data if successful
		poll.firePoll(success ->
		{
			polls.add(poll);
			DMain.updateServerData();
			event.reply("Poll added!").queue();
		}, failure ->
		{
			DMain.log.error("Failed to add poll", failure);
			event.reply("Something went wrong adding the poll").queue();
		});
	}
	
	/**
	 * Remove this member from nomination
	 */
	public void removeMember(JDA jda, long id)
	{
		// I'm intentionally not immediately removing them from memberCache, because that would mean they could leave and rejoin and spam polls
		// If this was the president
		if(hasPresident() && id == getPresidentID())
		{
			DMain.sendToOperator("The President left the server!");
			impeachPresident(jda.getGuildById(DMain.SERVER_ID));
		}
		
		// Remove member from candidates
		// If there are any candidates, it implies a vote is active
		Iterator<Candidate> iterator = DMain.server.getCandidates().iterator();
		
		while(iterator.hasNext())
		{
			Candidate member = iterator.next();
			
			if(member.getID() == id)
			{
				DMain.sendToOperator("A candidate left the running, maybe check its still good?");
				DMain.log.info("Removing candidate from running");
				iterator.remove();
				
				// Remove the reaction that belonged to it
				getPresidentialVote(jda).getReaction(Emoji.fromUnicode(DMain.server.slotToReaction(member.getSlot()))).removeReaction().queue();
				updatePresidentialVote(jda);
				return;
			}
		}
	}
	
	public long getPresidentID()
	{
		return presidentID;
	}
	
	public boolean hasPresident()
	{
		return presidentID != 0;
	}
	
	public boolean isLastTerm()
	{
		// Amendment 2
		return false;
		// return lastTerm;
	}
	
	public long getTermEndTime()
	{
		return termEndTime;
	}
	
	public long millisRemainingInTerm()
	{
		return Math.max(0, termEndTime - System.currentTimeMillis());
	}
	
	public int getAmendments()
	{
		return amendmentIDs.size();
	}
	
	public String getPresidentialSlogan()
	{
		return slogan;
	}
	
	public void setPresidentialSlogan(String slogan)
	{
		this.slogan = slogan;
		DMain.updateServerData();
	}
	
	public void tick(JDA jda)
	{
		// If we should update CAQ
		if(System.currentTimeMillis() - lastCAQ >= CAQ_UPDATE_TIME)
		{
			lastCAQ = System.currentTimeMillis();
			DMain.log.info("Updating CAQ");
			updateCAQ(jda);
		}
		
		// Check member cache for deletions
		boolean dataChanged = false;
		
		for(Iterator<ServerMember> iterator = memberCache.iterator(); iterator.hasNext() && (iterator.next()).shouldDelete();)
		{
			DMain.log.info("Marking member for deletion");
			dataChanged = true;
			iterator.remove();
		}
		
		// If someone was deleted from cache
		if(dataChanged)
		{
			DMain.updateServerData();
		}
		
		// Ensure presidentialVote is updated in case it crashed
		// This will fetch the message object using the stored presidentialVoteMessageID
		presidentialVote = getPresidentialVote(jda);
		
		// If we're voting for President
		if(presidentialVote != null || DMain.server.millisRemainingInTerm() < Server.PRESIDENTIAL_VOTE_TIME)
		{
			// If a poll needs to be created
			if(presidentialVote == null)
			{
				DMain.log.info("Opening up Presidential vote");
				
				// Add President as a re-election
				if(DMain.server.hasPresident() && !DMain.server.isLastTerm())
				{
					// Always the first slot, 0
					candidates.add(new Candidate(0, DMain.server.getPresidentID(), DMain.server.getPresidentialSlogan(), DMain.THE_PRESIDENT));
				}
				
				// Create vote, add first reaction (President re-election)
				presidentialVote = jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).sendMessageEmbeds(buildPresidentialVote()).complete();
				presidentialVoteMessageID = presidentialVote.getIdLong();
				
				if(!candidates.isEmpty())
					presidentialVote.addReaction(Emoji.fromUnicode("U+31U+fe0fU+20e3")).queue();
				
				DMain.updateServerData();
			}
			// Tick vote if already created
			else
			{
				// Decide vote if over a day
				if(System.currentTimeMillis() - presidentialVote.getTimeCreated().toInstant().toEpochMilli() > Server.PRESIDENTIAL_VOTE_TIME)
				{
					// Each tick when we can decide the winner, keep checking if we have new candidates
					// This could be empty for a while if no one runs (theoretically impossible because the President can't drop)
					// But if we were to add a dropping feature this should handle it
					if(!candidates.isEmpty())
					{
						// Tally votes
						int[] votes = new int[10];
						
						// Update message to get reactions
						presidentialVote = jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).retrieveMessageById(presidentialVoteMessageID).complete();
						
						for(MessageReaction r : presidentialVote.getReactions())
						{
							EmojiUnion emoji = r.getEmoji();
							if(emoji.getType() == Emoji.Type.CUSTOM)
								continue;
							
							// Inefficient way to add votes because fuck you
							for(int i = 0; i < 10; i++)
							{
								String unicode = slotToReaction(i);
								
								if(unicode.equals(emoji.asUnicode().getAsCodepoints()))
								{
									votes[i] = r.getCount();
								}
							}
						}
						
						List<Candidate> tiedCandidates = new ArrayList<Candidate>();
						int maxVotes = Arrays.stream(votes).max().orElse(votes[0]);
						
						DMain.log.info("Counting presidential votes");
						
						// For each candidate, add the top ones to the array
						for(int i = 0; i < candidates.size(); i++)
						{
							Candidate candidate = candidates.get(i);
							DMain.log.info(candidate.getID() + " " + votes[i]);
							
							if(votes[i] == maxVotes)
							{
								DMain.log.info("Adding {} to the tied candidates array", candidate.getID());
								tiedCandidates.add(candidate);
							}
						}
						
						// Determine if there's a tie. By logic, there must be at least 1
						Candidate nextPresident = tiedCandidates.get(0);
						
						if(tiedCandidates.size() > 1)
						{
							DMain.log.info("We have a tie! {}", tiedCandidates);
							nextPresident = tiedCandidates.get((int) (Math.random() * tiedCandidates.size()));
						}
						
						// President is elected
						candidates.clear();
						DMain.log.info(nextPresident.getID() + " won");
						Guild guild = jda.getGuildById(DMain.SERVER_ID);
						
						// Remove President roll
						if(DMain.server.hasPresident())
						{
							guild.removeRoleFromMember(guild.retrieveMemberById(DMain.server.getPresidentID()).complete(), DMain.THE_PRESIDENT).complete();
						}
						
						// Delete Presidential vote
						presidentialVote.delete().queue();
						presidentialVote = null;
						presidentialVoteMessageID = 0;
						
						// Transfer power
						if(presidentID == nextPresident.getID())
						{
							DMain.log.info("Same President " + presidentID);
							lastTerm = true;
						}
						else
						{
							DMain.log.info("Elected new president " + presidentID);
						}
						
						presidentID = nextPresident.getID();
						slogan = nextPresident.getSlogan();
						termEndTime = System.currentTimeMillis() + TERM_LENGTH;
						
						// This gets scooped up and deleted by checkMessageForPollResult below
						guild.getTextChannelById(DMain.VOTING_BOOTH).sendMessage("Welcome <@" + nextPresident.getID() + "> to The White House!").queue();//.complete().delete().queueAfter(1, TimeUnit.HOURS);
						
						Member nextPresidentMember = guild.retrieveMemberById(nextPresident.getID()).complete();
						guild.addRoleToMember(nextPresidentMember, DMain.THE_PRESIDENT).complete();
						
						// New president
						this.presidentialCount++;
						
						// Add to commanders and queefs
						Role safeParty = Optional.ofNullable(guild.getRoleById(nextPresident.getRoleID())).orElse(DMain.THE_PRESIDENT);
						EmbedBuilder e = new EmbedBuilder();
						e.setTitle(ordinal(DMain.server.getPresidentialCount()) + " President of Discordias, **" + MarkdownSanitizer.escape(nextPresidentMember.getUser().getEffectiveName()) + "**");
						e.setColor(safeParty.getColor());
						e.setDescription("<@" + nextPresidentMember.getId() + ">" + " of **" + MarkdownSanitizer.escape(safeParty.getName()) + "**\n\n*\"" + slogan + "\"*");
						e.setImage(nextPresidentMember.getEffectiveAvatarUrl());
						e.setFooter("Served " + getUSEnglishDateFormat(DMain.server.getTermEndTime() - Server.TERM_LENGTH) + " - " + getUSEnglishDateFormat(DMain.server.getTermEndTime()), jda.getSelfUser().getEffectiveAvatarUrl());
						jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.COMMANDERS_AND_QUEEFS).sendMessageEmbeds(e.build()).queue(success ->
						{
							caqEntries.put(success.getId(), nextPresidentMember.getId());
							
							// Update data
							DMain.updateServerData();
						});
						
						// Update all CAQs for potential out of date URLs
						updateCAQ(jda);
					}
				}
			}
		}
	}
	
	private void updateCAQ(JDA jda)
	{
		if(DMain.inIDE)
		{
			System.err.println("Not updating CAQ in IDE");
			return;
		}
		
		Guild guild = jda.getGuildById(DMain.SERVER_ID);
		
		// Edge condition idc about is this can cause memory leaks. I'll worry about this in the year 10000
		guild.getTextChannelById(DMain.COMMANDERS_AND_QUEEFS).getIterableHistory().forEach(message ->
		{
			MessageEmbed embed = message.getEmbeds().get(0);
			EmbedBuilder builder = new EmbedBuilder(embed);
			
			// Check if user and role still exist
			// onErrorMap catches things AFTER the event is fired. retrieveUserById can still throw early for bad format
			// But this works if the ID is valid and Discord can't find the user associated with it. User then becomes null!
			User user = jda.retrieveUserById(caqEntries.get(message.getId())).onErrorMap(throwable -> null).complete();
			
//			if(user != null)
//			{
//				builder.setTitle(ordinal(DMain.server.getPresidentialCount()) + " President of Discordias, **" + MarkdownSanitizer.sanitize(user.getEffectiveName()) + "**");
//			}
			
			// Update image in case user changes pfp, or user is magically deleted
			builder.setImage(user != null ? user.getEffectiveAvatarUrl() : "https://cdn.discordapp.com/embed/avatars/0.png");
			
			message.editMessageEmbeds(builder.build()).complete();
		});
	}
	
	public static String getUSEnglishDateFormat(long time)
	{
		return new SimpleDateFormat("MM/dd/yyyy").format(new Date(time));
	}
	
	public static String ordinal(int i)
	{
		String[] suffixes = new String[] {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
		
		switch(i % 100)
		{
			case 11:
			case 12:
			case 13:
				return i + "th";
			default:
				return i + suffixes[i % 10];
		}
	}
	
	/**
	 * Converts a {@linkplain Candidate} slot to their corresponding reaction unicode.
	 * 
	 * @param slot candidate slot
	 * @return emoji unicode
	 */
	public String slotToReaction(int slot)
	{
		return slot == 9 ? "U+1f51f" : ("U+3" + (slot + 1) + "U+fe0fU+20e3");
	}
	
	private MessageEmbed buildPresidentialVote()
	{
		EmbedBuilder e = new EmbedBuilder();
		e.setTitle(Server.ordinal(getPresidentialCount() + 1) + " Presidential Election");
		e.setColor(Color.red);
		
		StringBuilder builder = new StringBuilder("@everyone it's time. By the power of the people and the Magna Farta, we will elect our next monthly President that represents the core of this nation's beliefs and thereby representing the people. Cast your vote below:\n");
		
		// Sort candidates by their slot
		DMain.server.candidates.stream().sorted((o1, o2) -> Integer.compare(o1.getSlot(), o2.getSlot())).forEach((candidate) ->
		{
			// no need to sanitize, it's handled in the EventHandler
			builder.append("\n**#" + (candidate.getSlot() + 1) + ": <@" + candidate.getID() + ">** (<@&" + candidate.getRoleID() + ">) - *\"" + candidate.getSlogan() + "\"*");
		});
		
		if(DMain.server.getCandidates().isEmpty())
			builder.append("\n*There are no active presidential candidates. Run for office with " + DMain.getCommandReference("campaign") + ".");
		
		e.setImage("https://cdn.discordapp.com/app-icons/910579031391498330/c65afb3995baa1c31212e43f1f643e7e.png");
		e.setDescription(builder.toString());
		e.setFooter("Vote will be decided in " + (int) (Server.PRESIDENTIAL_VOTE_TIME / 3.6e+6) + " hours. Thank you for being an active participant in our perfect society.");
		return e.build();
	}
	
	/**
	 * Defaults / nulls President values and removes the role from the President.
	 */
	public void impeachPresident(Guild guild)
	{
		DMain.log.info("Impeached President");
		
		// Change records for historical accuracy. Latest message in CAQ is the current president
		TextChannel channel = guild.getJDA().getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.COMMANDERS_AND_QUEEFS);
		channel.retrieveMessageById(channel.getLatestMessageId()).queue(message ->
		{
			MessageEmbed embed = message.getEmbeds().get(0);
			String footer = embed.getFooter().getText();
			message.editMessageEmbeds(EmbedBuilder.fromData(embed.toData()).setFooter(footer.substring(footer.indexOf("-") + 2) + " impeached " + getUSEnglishDateFormat(System.currentTimeMillis())).build()).queue();
		});
		
		// Reset data
		presidentID = 0;
		termEndTime = System.currentTimeMillis();
		slogan = null;
		lastTerm = false;
		DMain.updateServerData();
		
		// Remove presidential role
		guild.retrieveMemberById(DMain.server.getPresidentID()).submit().thenApply(member -> guild.removeRoleFromMember(member, DMain.THE_PRESIDENT));
	}
	
	public void addAmendment(JDA jda, String content)
	{
		jda.getTextChannelById(DMain.AMENDMENTS).sendMessage("**Amendment #" + (getAmendments() + 1) + "** - " + MarkdownSanitizer.sanitize(content)).queue(success ->
		{
			DMain.log.info("Added amendment {}", content);
			amendmentIDs.add(success.getId());
			DMain.updateServerData();
		}, failure ->
		{
			DMain.log.error("Failed to add amendment {}", content, failure);
		});
	}
	
	public void repealAmendment(JDA jda, int number)
	{
		Message message = jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(amendmentIDs.get(number)).complete();
		String raw = message.getContentRaw();
		
		if(!raw.startsWith("~~") && !raw.endsWith("~~"))
		{
			message.editMessage("~~" + message.getContentRaw() + "~~").complete();
		}
		else
		{
			message.editMessage(raw.substring(2, raw.length() - 2)).complete();
		}
	}
	
	public String getAmendment(JDA jda, int index)
	{
		return jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(amendmentIDs.get(index)).complete().getContentRaw();
	}
	
	public void addSecret(String word, String response)
	{
		unsecretedCommands.remove(word);
		secretCommands.put(word.toLowerCase(), response);
		DMain.updateServerData();
	}
	
	/**
	 * Unsecrets a secret command.
	 * 
	 * @param word secret command
	 * @return true if the secret command was removed, false if the mapping doesn't exist
	 */
	public boolean unsecret(String word)
	{
		String mapping = secretCommands.remove(word);
		// Exit early if not found
		if(mapping == null)
			return false;
		
		// Transfer secret command to unsecreted commands
		unsecretedCommands.put(word, mapping);
		DMain.updateServerData();
		return true;
	}
	
	/**
	 * Resecrets a secret command.
	 * 
	 * @param word secret command
	 * @return true if the secret command was readded from unsecreted commands, false if it never existed in unsecreted commands
	 */
	public boolean resecretSecret(String word)
	{
		String mapping = unsecretedCommands.remove(word);
		if(mapping == null)
			return false;
		
		// This won't override anything because adding secret commands removes it from unsecreted
		secretCommands.put(word, mapping);
		DMain.updateServerData();
		return true;
	}
	
	public Map<String, String> getSecretCommands()
	{
		return secretCommands;
	}
	
	public List<Candidate> getCandidates()
	{
		return candidates;
	}
	
	public String removeAmendment(JDA jda, int number)
	{
		if(number > 0 && number <= amendmentIDs.size())
		{
			// Decrement to indices
			number--;
			// Delete OG message
			jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(amendmentIDs.get(number)).complete().delete().complete();
			// Remove from data
			return "Deleted " + amendmentIDs.remove(number);
		}
		
		DMain.updateServerData();
		return "Did not find amendment with ID " + number;
	}
	
	public void updatePresidentialVote(JDA jda)
	{
		getPresidentialVote(jda).editMessageEmbeds(buildPresidentialVote()).complete();
		DMain.updateServerData();
	}
	
	/**
	 * @return next available slot ID, or -1 if all are taken (0 - 9)
	 */
	public int getNextCandidateSlot()
	{
		for(int slot = 0; slot < 10; slot++)
		{
			boolean found = false;
			
			for(Candidate c : candidates)
			{
				if(c.getSlot() == slot)
				{
					found = true;
					break;
				}
			}
			
			// If none of the candidates has this slot
			if(!found)
				return slot;
		}
		
		return -1;
	}
	
	/**
	 * Checks message only if it's Voting Booth for a poll result message.
	 * 
	 * @param message message to check
	 */
	public void checkMessageForPollResult(Message message)
	{
		if(message.getChannel().getIdLong() != DMain.VOTING_BOOTH)
			return;
		
		// ALL messages beyond this point will be deleted after an hour
		boolean pollDeleted = false;
		
		// Check if voting booth && it's something important
		if(message.getType() == MessageType.POLL_RESULT)
		{
			// If it's a poll result, it's assumed the following logic will work to grab the referenced poll
			long pollID = message.getMessageReference().getMessageIdLong();
			Iterator<Poll> iterator = polls.iterator();
			
			// For each poll in server data
			while(iterator.hasNext())
			{
				Poll sample = iterator.next();
				
				// If this active poll matches the message
				if(sample.getMessageID() == pollID)
				{
					// The poll is done
					DMain.log.info("Received poll end message");
					sample.endPoll(message.getJDA());
					iterator.remove();
					pollDeleted = true;
				}
				else
				{
					// Check for any active polls lingering around in server data by checking if their voting time expired a long time ago
					if(sample.getStartTime() + sample.getVoteTime().toMillis() * 2 < System.currentTimeMillis())
					{
						DMain.log.error("Weird. {} poll stuck around much longer than it should've. Perhaps it doesn't exist?", sample.getFancyName());
						iterator.remove();
						pollDeleted = true;
					}
				}
			}
		}
		
		// If this message isn't the presidential election AND it's not a voting poll, but if it is, don't delete it if not expired
		if(!DMain.inIDE && message.getIdLong() != presidentialVoteMessageID && Optional.ofNullable(message.getPoll()).map(MessagePoll::isExpired).orElse(true))
		{
			DMain.log.info("Deleting message with ID {}", message.getId());
			// Delete after a while
			message.delete().queueAfter(1, TimeUnit.HOURS, null, (e) ->
			{
				DMain.log.error("Tried to delete a message that no longer exists: {}", message.getContentRaw(), e);
			});
		}
		
		// Update server data only if something changed
		if(pollDeleted)
		{
			DMain.updateServerData();
		}
	}
	
	@Override
	public String toString()
	{
		return JsonUtils.serialize(this);
	}
}
