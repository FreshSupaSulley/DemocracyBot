package io.github.freshsupasulley.dbot;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import io.github.freshsupasulley.dbot.polls.Poll;
import io.github.freshsupasulley.dbot.utils.JsonUtils;
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
import net.dv8tion.jda.api.entities.channel.concrete.Category;
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
	private List<Poll<?>> polls = new ArrayList<Poll<?>>();
	// Times of proposing each type of poll. Purposely disassociating this from ServerMember for leaving server functionality / avoid spamming poll abuse
	private Map<Long, Map<String, Long>> pollCooldownExpiryTimes = new HashMap<Long, Map<String, Long>>();
	private List<ServerMember> members = new ArrayList<ServerMember>();
	private Map<Long, PoliticalParty> parties = new HashMap<Long, PoliticalParty>();
	private Set<Long> naturalizedCitizens = new HashSet<Long>(); // Set automatically prevents duplicates
	private long presidentID;
	private String slogan;
	private long termEndTime;
	
	// For /refer
	// Amendment index -> text, expiration time (epoch millis)
	// Honestly this doesn't even really need to be a thing. We're just continuing the pain of making the text channel the SoT when it could be based off serverData
	private transient Map<Integer, Entry<String, Long>> amendmentCache = new HashMap<Integer, Map.Entry<String, Long>>();
	private static final long AMENDMENT_CACHE_TIME = 86400000L; // one day
	
	@SuppressWarnings("unused")
	private boolean lastTerm;
	
	// Presidential vote
	// Try to store these. Start of program should correct them if it can't be found
	private long presidentialVoteMessageID;
	private ArrayList<Candidate> candidates = new ArrayList<Candidate>();
	
	// Don't serialize
	private transient Message presidentialVote;
	private transient long lastCAQ = System.currentTimeMillis();
	
	public Message getPresidentialVote(JDA jda)
	{
		if(!isElectionActive())
			return null;
		
		// If in cache
		if(presidentialVote != null)
			return presidentialVote;
		
		// Otherwise retrieve it
		return jda.getGuildById(Main.SERVER_ID).getTextChannelById(Main.VOTING_BOOTH).retrieveMessageById(presidentialVoteMessageID).onErrorMap(error ->
		{
			Main.log.error("Huh? Tried to fetch presidential vote but fail checks failed", error);
			return null;
		}).onSuccess(message ->
		{
			this.presidentialVote = message;
		}).complete();
	}
	
	// public boolean isNaturalized(long userID)
	// {
	// return naturalizedCitizens.stream().anyMatch(sample -> sample == userID);
	// }
	
	public boolean isNaturalized(Member member)
	{
		// If they have the citizen role
		if(member.getRoles().stream().anyMatch(role -> role.getIdLong() == Main.CITIZEN_ID))
		{
			// Also ensure they're in naturalized set
			if(naturalizedCitizens.add(member.getIdLong()))
			{
				Main.log.warn("{} is a citizen but wasn't in the naturalization list", member);
				Main.updateServerData();
			}
			
			return true;
		}
		// If they're in the set but don't have the role
		else if(naturalizedCitizens.contains(member.getIdLong()))
		{
			naturalize(member);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Adds the citizen role to the member, even if they're already naturalized internally. Useful if we want to ensure they have the citizen role safely.
	 * 
	 * @param member member
	 */
	public void naturalize(Member member)
	{
		// If we didn't already have them naturalized
		if(naturalizedCitizens.add(member.getIdLong()))
		{
			Main.updateServerData();
		}
		
		// Add the role (doesn't do anything besides lets people know they're naturalized)
		// If they already have it, this does nothing (safe)
		Guild guild = member.getJDA().getGuildById(Main.SERVER_ID);
		guild.addRoleToMember(member, guild.getRoleById(Main.CITIZEN_ID)).queue(success ->
		{
			Main.log.info("Added citizen role to member");
		}, e ->
		{
			Main.log.error("Failed to apply citizen role to naturalize new citizen: {}", member, e);
		});
	}
	
	public boolean isElectionActive()
	{
		return presidentialVoteMessageID != 0;
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
	
	public boolean partyNameCollision(Guild guild, String name)
	{
		return !guild.getRolesByName(name, false).isEmpty();
	}
	
	/**
	 * Ensure this person isn't endlessly requesting this poll. If the member can propose, the cooldown is reset.
	 * 
	 * <p>
	 * This does <b>NOT</b> check if the member is naturalized.
	 * </p>
	 * 
	 * @param poll poll to check
	 * @return true if the member can propose the poll, false otherwise
	 */
	public boolean meetsCooldown(ServerMember member, Poll<?> poll)
	{
		// Need this for testing
		if(Main.inIDE && member.getID() == Main.OWNER_ID)
			return true;
		
		// Get or create the cooldown map for this member
		Map<String, Long> cooldowns = pollCooldownExpiryTimes.computeIfAbsent(member.getID(), k -> new HashMap<String, Long>());
		
		String pollType = poll.getClass().getName();
		long now = System.currentTimeMillis();
		long expiryTime = cooldowns.getOrDefault(pollType, 0L);
		
		if(now >= expiryTime)
		{
			cooldowns.put(pollType, now + poll.getVotingCooldown());
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns the time remaining in the poll cooldown.
	 * 
	 * @param poll poll to check
	 * @return time remaining in milliseconds before the member can request again
	 */
	public long getMillisRemaining(ServerMember member, Poll<?> poll)
	{
		Map<String, Long> cooldowns = pollCooldownExpiryTimes.getOrDefault(member.getID(), Collections.emptyMap());
		long expiry = cooldowns.getOrDefault(poll.getClass().getName(), 0L);
		return Math.max(0, expiry - System.currentTimeMillis());
	}
	
	/**
	 * Removes all cooldowns of this member.
	 */
	public void clearCooldowns()
	{
		Main.server.pollCooldownExpiryTimes.clear();
		Main.updateServerData();
	}
	
	public ServerMember getMember(Member user)
	{
		return getMember(user.getIdLong());
	}
	
	/**
	 * Probably only wanna use this one internally to avoid passing the wrong value.
	 * 
	 * @param id Discord user ID
	 * @return new {@link ServerMember} instance
	 */
	private ServerMember getMember(long id)
	{
		// Now that we have the server, search for member within server
		for(ServerMember member : members)
		{
			// If the user already exists, move to front of list
			if(member.getID() == id)
			{
				return member;
			}
		}
		
		// If we couldn't find user / server, the ServerMember is new
		ServerMember initMember = new ServerMember(id);
		members.add(initMember);
		Main.updateServerData();
		return initMember;
	}
	
	public void createPoliticalParty(PoliticalParty party)
	{
		parties.put(party.getRole(), party);
	}
	
	public boolean isParty(Role party)
	{
		return getParty(party.getIdLong()) != null;
	}
	
	@Nullable
	public PoliticalParty getParty(long partyID)
	{
		return parties.get(partyID);
	}
	
	public List<ServerMember> getPartyMembers(PoliticalParty party)
	{
		return members.stream().filter(member -> Optional.ofNullable(member.getPoliticalParty()).map(sample -> sample.getRole() == party.getRole()).orElse(false)).collect(Collectors.toList());
	}
	
	/**
	 * Starts a poll if another doesn't already exist.
	 * 
	 * @param poll poll to start
	 * @return user response, indicating if it the request was successful
	 */
	public void beginPoll(SlashCommandInteractionEvent event, Poll<?> poll)
	{
		// Ensure a duplicate poll doesn't exist
		for(Poll<?> sample : polls)
		{
			if(poll.equals(sample))
			{
				event.reply("Another poll of this kind already exists!").setEphemeral(true).queue();
				return;
			}
		}
		
		// Ensure no one is spamming the poll
		ServerMember member = getMember(event.getMember());
		
		// This HAS to be the last thing checked, as it will add a cooldown to the command
		if(!member.canPropose(poll))
		{
			float millisLeft = (int) (member.getMillisRemaining(poll) / 3600000F * 100) / 100F;
			event.reply("You cannot " + Main.getCommandReference(event.getFullCommandName()) + " this frequently (" + (millisLeft < 0.1f ? "< 0.1" : millisLeft) + "hr cooldown)").setEphemeral(true).queue();
			return;
		}
		
		// Only update the server data if successful
		poll.firePoll(success ->
		{
			polls.add(poll);
			Main.updateServerData();
			event.reply("Poll added!").queue();
		}, failure ->
		{
			Main.log.error("Failed to add poll", failure);
			event.reply("Something went wrong adding the poll").queue();
		});
	}
	
	/**
	 * Remove this member from nomination
	 */
	public void removeMember(JDA jda, long id)
	{
		// ~~I'm intentionally not immediately removing them from memberCache, because that would mean they could leave and rejoin and spam polls~~
		// ^ not anymore (handled at the bottom)! Cooldowns are now decoupled from server member instances
		
		// If this was the president
		if(hasPresident() && id == getPresidentID())
		{
			Main.sendToOperator("The President left the server!");
			impeachPresident(jda.getGuildById(Main.SERVER_ID));
		}
		
		// Remove member from candidates
		// If there are any candidates, it implies a vote is active
		for(Iterator<Candidate> iterator = Main.server.getCandidates().iterator(); iterator.hasNext();)
		{
			Candidate member = iterator.next();
			
			if(member.getID() == id)
			{
				iterator.remove();
				
				Main.sendToOperator("A candidate left the running, maybe check that the poll is still functional?");
				Main.log.info("Removing candidate from running");
				
				// Remove the reaction that belonged to it
				getPresidentialVote(jda).getReaction(Emoji.fromUnicode(Main.server.slotToReaction(member.getSlot()))).removeReaction().queue();
				updatePresidentialVote(jda);
				break;
			}
		}
		
		// Remove member from data
		for(Iterator<ServerMember> iterator = members.iterator(); iterator.hasNext();)
		{
			ServerMember member = iterator.next();
			
			if(member.getID() == id)
			{
				iterator.remove();
				Main.log.info("A server member left: {}", member.getID());
				
				// Handle parties (we don't need the iterator this time)
				PoliticalParty party = member.getPoliticalParty();
				
				// If we're the leader of a party
				if(party != null && party.getLeaderID() == id)
				{
					deletePartyAndChannels(party, jda.getGuildById(Main.SERVER_ID));
				}
				
				break;
			}
		}
	}
	
	public CompletableFuture<Boolean> deletePartyAndChannels(PoliticalParty party, Guild guild)
	{
		// First, handle data by kicking members out of this party and deleting it from storage
		members.stream().filter(member -> party.equals(member.getPoliticalParty())).forEach(member -> member.setPoliticalParty(null));
		parties.remove(party.getRole());
		Main.updateServerData();
		
		// Now delete all artifacts
		Category category = guild.getCategoryById(party.getCategory());
		Role role = guild.getRoleById(party.getRole());
		
		if(role == null || category == null)
		{
			Main.log.error("Role ({}) or category ({}) is null when trying to delete party", role, category);
			return CompletableFuture.completedFuture(false);
		}
		
		// Deleting a category decategorizes inner channels instead of cascading the delete so this is required
		return CompletableFuture.allOf(category.getChannels().stream().map(channel -> channel.delete().submit()).toArray(CompletableFuture[]::new)).thenCompose(__ -> category.delete().submit()).thenCompose(__ -> role.delete().submit()).thenApply(__ -> true).exceptionally(e ->
		{
			Main.log.error("Failed to delete party roles / attributes", e);
			return false;
		});
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
		Main.updateServerData();
	}
	
	public void tick(JDA jda)
	{
		// If we should update CAQ
		if(System.currentTimeMillis() - lastCAQ >= CAQ_UPDATE_TIME)
		{
			lastCAQ = System.currentTimeMillis();
			Main.log.info("Updating CAQ");
			updateCAQ(jda);
		}
		
		// Ensure presidentialVote is updated in case it crashed
		// This will fetch the message object using the stored presidentialVoteMessageID
		presidentialVote = getPresidentialVote(jda);
		
		// If we're voting for President
		if(presidentialVote != null || Main.server.millisRemainingInTerm() < Server.PRESIDENTIAL_VOTE_TIME)
		{
			// If a poll needs to be created
			if(presidentialVote == null)
			{
				Main.log.info("Opening up Presidential vote");
				
				// Add President as a re-election
				if(Main.server.hasPresident() && !Main.server.isLastTerm())
				{
					// Always the first slot, 0
					// Because of the conditional we should be guaranteed to find a president
					candidates.add(new Candidate(getMember(presidentID), 0, Main.server.getPresidentialSlogan()));
				}
				
				// Create vote, add first reaction (President re-election)
				presidentialVote = jda.getGuildById(Main.SERVER_ID).getTextChannelById(Main.VOTING_BOOTH).sendMessageEmbeds(buildPresidentialVote()).complete();
				presidentialVoteMessageID = presidentialVote.getIdLong();
				
				if(!candidates.isEmpty())
					presidentialVote.addReaction(Emoji.fromUnicode("U+31U+fe0fU+20e3")).queue();
				
				Main.updateServerData();
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
						presidentialVote = jda.getGuildById(Main.SERVER_ID).getTextChannelById(Main.VOTING_BOOTH).retrieveMessageById(presidentialVoteMessageID).complete();
						
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
						
						Main.log.info("Counting presidential votes");
						
						// For each candidate, add the top ones to the array
						for(int i = 0; i < candidates.size(); i++)
						{
							Candidate candidate = candidates.get(i);
							Main.log.info(candidate.getID() + " " + votes[i]);
							
							if(votes[i] == maxVotes)
							{
								Main.log.info("Adding {} to the tied candidates array", candidate.getID());
								tiedCandidates.add(candidate);
							}
						}
						
						// Determine if there's a tie. By logic, there must be at least 1
						Candidate nextPresident = tiedCandidates.get(0);
						
						if(tiedCandidates.size() > 1)
						{
							Main.log.info("We have a tie! {}", tiedCandidates);
							nextPresident = tiedCandidates.get((int) (Math.random() * tiedCandidates.size()));
						}
						
						// President is elected
						candidates.clear();
						Main.log.info(nextPresident.getID() + " won");
						Guild guild = jda.getGuildById(Main.SERVER_ID);
						
						// Remove President roll
						if(Main.server.hasPresident())
						{
							guild.removeRoleFromMember(guild.retrieveMemberById(Main.server.getPresidentID()).complete(), Main.THE_PRESIDENT).complete();
						}
						
						// Delete Presidential vote
						presidentialVote.delete().queue();
						presidentialVote = null;
						presidentialVoteMessageID = 0;
						
						// Transfer power
						if(presidentID == nextPresident.getID())
						{
							Main.log.info("Same President " + presidentID);
							lastTerm = true;
						}
						else
						{
							Main.log.info("Elected new president " + presidentID);
						}
						
						presidentID = nextPresident.getID();
						slogan = nextPresident.getSlogan();
						termEndTime = System.currentTimeMillis() + TERM_LENGTH;
						
						// This gets scooped up and deleted by checkMessageForPollResult below
						guild.getTextChannelById(Main.VOTING_BOOTH).sendMessage("Welcome <@" + nextPresident.getID() + "> to The White House!").queue();// .complete().delete().queueAfter(1, TimeUnit.HOURS);
						
						Member nextPresidentMember = guild.retrieveMemberById(nextPresident.getID()).complete();
						guild.addRoleToMember(nextPresidentMember, Main.THE_PRESIDENT).complete();
						
						// New president
						this.presidentialCount++;
						
						// Add to commanders and queefs
						Role safeParty = Optional.ofNullable(guild.getRoleById(nextPresident.getPoliticalParty().getRole())).orElse(Main.THE_PRESIDENT); // I am PRAYING that the role exists because of the "don't leave if candidate" check
						EmbedBuilder e = new EmbedBuilder();
						e.setTitle(ordinal(Main.server.getPresidentialCount()) + " President of Discordias, **" + MarkdownSanitizer.escape(nextPresidentMember.getUser().getEffectiveName()) + "**");
						e.setColor(safeParty.getColor());
						e.setDescription("<@" + nextPresidentMember.getId() + ">" + " of **" + MarkdownSanitizer.escape(safeParty.getName()) + "**\n\n*\"" + slogan + "\"*");
						e.setImage(nextPresidentMember.getEffectiveAvatarUrl());
						e.setFooter("Served " + getUSEnglishDateFormat(Main.server.getTermEndTime() - Server.TERM_LENGTH) + " - " + getUSEnglishDateFormat(Main.server.getTermEndTime()), jda.getSelfUser().getEffectiveAvatarUrl());
						jda.getGuildById(Main.SERVER_ID).getTextChannelById(Main.COMMANDERS_AND_QUEEFS).sendMessageEmbeds(e.build()).queue(success ->
						{
							caqEntries.put(success.getId(), nextPresidentMember.getId());
							
							// Update data
							Main.updateServerData();
						}, error ->
						{
							Main.log.error("Failed to create CAQ entry", error);
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
		if(Main.inIDE)
		{
			System.err.println("Not updating CAQ in IDE");
			return;
		}
		
		Guild guild = jda.getGuildById(Main.SERVER_ID);
		
		// Edge condition idc about is this can cause memory leaks. I'll worry about this in the year 10000
		guild.getTextChannelById(Main.COMMANDERS_AND_QUEEFS).getIterableHistory().forEach(message ->
		{
			MessageEmbed embed = message.getEmbeds().get(0);
			EmbedBuilder builder = new EmbedBuilder(embed);
			
			// Check if user and role still exist
			// onErrorMap catches things AFTER the event is fired. retrieveUserById can still throw early for bad format
			// But this works if the ID is valid and Discord can't find the user associated with it. User then becomes null!
			User user = jda.retrieveUserById(caqEntries.get(message.getId())).onErrorMap(throwable -> null).complete();
			
			// if(user != null)
			// {
			// builder.setTitle(ordinal(DMain.server.getPresidentialCount()) + " President of Discordias, **" + MarkdownSanitizer.sanitize(user.getEffectiveName()) + "**");
			// }
			
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
		Main.server.candidates.stream().sorted((o1, o2) -> Integer.compare(o1.getSlot(), o2.getSlot())).forEach((candidate) ->
		{
			// no need to sanitize, it's handled in the EventHandler
			// The only person who may not be in a political party would be the president, in which case the President role is used. Also helps to indicate re-elections in
			// CAQ
			builder.append("\n**#" + (candidate.getSlot() + 1) + ": <@" + candidate.getID() + ">** (<@&" + Optional.ofNullable(candidate.getPoliticalParty()).map(party -> party.getRole()).orElse(Main.THE_PRESIDENT_ID) + ">) - *\"" + candidate.getSlogan() + "\"*");
		});
		
		if(Main.server.getCandidates().isEmpty())
			builder.append("\n*There are no active presidential candidates. Run for office with " + Main.getCommandReference("campaign") + ".*");
		
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
		Main.log.info("Impeached President");
		
		// Change records for historical accuracy. Latest message in CAQ is the current president
		TextChannel channel = guild.getJDA().getGuildById(Main.SERVER_ID).getTextChannelById(Main.COMMANDERS_AND_QUEEFS);
		channel.retrieveMessageById(channel.getLatestMessageId()).submit().thenCompose(message ->
		{
			MessageEmbed embed = message.getEmbeds().get(0);
			String footer = embed.getFooter().getText();
			return message.editMessageEmbeds(EmbedBuilder.fromData(embed.toData()).setFooter(footer.substring(footer.indexOf("-") + 2) + " impeached " + getUSEnglishDateFormat(System.currentTimeMillis())).build()).submit();
		});
		
		// Reset data
		presidentID = 0;
		termEndTime = System.currentTimeMillis();
		slogan = null;
		lastTerm = false;
		Main.updateServerData();
		
		// Remove presidential role
		guild.retrieveMemberById(Main.server.getPresidentID()).submit().thenApply(member -> guild.removeRoleFromMember(member, Main.THE_PRESIDENT));
	}
	
	/**
	 * Adds an amendment but escapes, not sanitizes, any markdown.
	 * 
	 * @param jda
	 * @param content
	 */
	public void addAmendment(JDA jda, String content)
	{
		jda.getTextChannelById(Main.AMENDMENTS).sendMessage("**Amendment #" + (getAmendments() + 1) + "** - " + MarkdownSanitizer.escape(content)).queue(success ->
		{
			Main.log.info("Added amendment {}", content);
			amendmentIDs.add(success.getId());
			amendmentCache.put(amendmentIDs.size() - 1, new SimpleEntry<String, Long>(content, System.currentTimeMillis() + AMENDMENT_CACHE_TIME));
			Main.updateServerData();
		}, failure ->
		{
			Main.log.error("Failed to add amendment {}", content, failure);
		});
	}
	
	public void repealAmendment(JDA jda, int index)
	{
		Message message = jda.getTextChannelById(Main.AMENDMENTS).retrieveMessageById(amendmentIDs.get(index)).complete();
		String raw = message.getContentRaw();
		
		String newContent = raw.startsWith("~~") && raw.endsWith("~~") ? raw.substring(2, raw.length() - 2) : "~~" + message.getContentRaw() + "~~";
		message.editMessage(newContent).queue();
		
		// Update cache
		amendmentCache.put(index, new SimpleEntry<>(newContent, System.currentTimeMillis() + AMENDMENT_CACHE_TIME));
	}
	
	/**
	 * Gets the amendment at the index.
	 * 
	 * @param jda   JDA
	 * @param index amendment index, <b>NOT</b> its number
	 * @return amendment text
	 */
	public String getAmendment(JDA jda, int index)
	{
		// Check if cache expired
		Entry<String, Long> entry = amendmentCache.get(index);
		
		// If we don't have the amendment in cache yet, or if we passed the expiration time
		if(!amendmentCache.containsKey(index) || System.currentTimeMillis() > entry.getValue())
		{
			Main.log.info("Amendment is not in cache, fetching amendment index {}", index);
			String amendment = jda.getTextChannelById(Main.AMENDMENTS).retrieveMessageById(amendmentIDs.get(index)).complete().getContentRaw();
			amendmentCache.put(index, new SimpleEntry<>(amendment, System.currentTimeMillis() + AMENDMENT_CACHE_TIME));
		}
		
		return amendmentCache.get(index).getKey();
	}
	
	public List<Candidate> getCandidates()
	{
		return candidates;
	}
	
	public boolean isCampaining(ServerMember sender)
	{
		return candidates.stream().anyMatch(candidate -> candidate.getID() == sender.getID());
	}
	
	public String removeAmendment(JDA jda, int index)
	{
		if(index > 0 && index <= amendmentIDs.size())
		{
			// Decrement to indices
			index--;
			// Delete OG message
			jda.getTextChannelById(Main.AMENDMENTS).retrieveMessageById(amendmentIDs.get(index)).complete().delete().queue();
			amendmentCache.remove(index);
			// Remove from data
			return "Deleted " + amendmentIDs.remove(index);
		}
		
		Main.updateServerData();
		return "Did not find amendment with ID " + index;
	}
	
	public void updatePresidentialVote(JDA jda)
	{
		getPresidentialVote(jda).editMessageEmbeds(buildPresidentialVote()).complete();
		Main.updateServerData();
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
		if(message.getChannel().getIdLong() != Main.VOTING_BOOTH)
			return;
		
		// ALL messages beyond this point will be deleted after an hour
		boolean pollDeleted = false;
		
		// If it's a poll result (special message that's generated by discord when a poll has finished)
		if(message.getType() == MessageType.POLL_RESULT)
		{
			// If it's a poll result, it's assumed the following logic will work to grab the referenced poll
			long pollID = message.getMessageReference().getMessageIdLong();
			Iterator<Poll<?>> iterator = polls.iterator();
			
			// For each poll in server data
			while(iterator.hasNext())
			{
				Poll<?> sample = iterator.next();
				
				// If this active poll matches the message
				if(sample.getMessageID() == pollID)
				{
					// The poll is done
					Main.log.info("Received poll end message");
					sample.endPoll(message.getJDA());
					iterator.remove();
					pollDeleted = true;
				}
				else
				{
					// Check for any active polls lingering around in server data by checking if their voting time expired a long time ago
					if(sample.getStartTime() + sample.getVoteTime().toMillis() * 2 < System.currentTimeMillis())
					{
						Main.log.error("Weird. {} poll stuck around much longer than it should've. Perhaps it doesn't exist?", sample);
						iterator.remove();
						pollDeleted = true;
					}
				}
			}
			
			// We don't need 2 differnet "poll ended" messages. We're deleting ths poll result message but dbot sends a more detailed summary
			message.delete().queue();
		}
		// If this message isn't the presidential election AND it's not a voting poll, but if it is, don't delete it if not expired
		else if(!Main.inIDE && message.getIdLong() != presidentialVoteMessageID && Optional.ofNullable(message.getPoll()).map(MessagePoll::isExpired).orElse(true))
		{
			Main.log.info("Deleting message with ID {}", message.getId());
			// Delete after a while
			message.delete().queueAfter(1, TimeUnit.HOURS, null, (e) ->
			{
				Main.log.error("Tried to delete a message that no longer exists: {}", message.getContentRaw(), e);
			});
		}
		
		// Update server data only if something changed
		if(pollDeleted)
		{
			Main.updateServerData();
		}
	}
	
	@Override
	public String toString()
	{
		return JsonUtils.serialize(this);
	}
}
