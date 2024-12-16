package democracy;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.supasulley.web.JsonUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class Server {
	
	/** Term length is 30 days */
	public static final long TERM_LENGTH = 2592000000L;
	/** Presidential vote time is 3 days */
	public static final long PRESIDENTIAL_VOTE_TIME = 259200000;
	
	// Everything has to be initialized in case it doesn't get deserialized and a new server obj is created
	// Serialize
	private int presidentialCount = 0;
	private Map<Long, Long> immigrants = new HashMap<Long, Long>();; // <End time, UserID>
	private List<String> amendmentIDs = new ArrayList<String>();
	private Map<String, String> secretCommands = new HashMap<String, String>();
	private List<Poll> polls = new ArrayList<Poll>();
	private List<ServerMember> memberCache = new ArrayList<ServerMember>();
	private long presidentID;
	private String slogan;
	private long termEndTime;
	private boolean lastTerm;
	
	// Presidential vote
	// Try to store these. Start of program should correct them if it can't be found
	private long presidentialVoteMessageID;
	private ArrayList<Candidate> candidates = new ArrayList<Candidate>();
	
	// Don't serialize
	private transient Message presidentialVote;
	
	public Message getPresidentialVote(JDA jda)
	{
		if(presidentialVoteMessageID == 0) return null;
		
		// If in cache
		if(presidentialVote != null) return presidentialVote;
		
		// Otherwise retrieve it
		return jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).retrieveMessageById(presidentialVoteMessageID).onErrorMap(error -> {
			DMain.error("Huh? Tried to fetch presidential vote but fail checks failed", error);
			return null;
		}).onSuccess(message -> {
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
	
	public void addMember(ServerMember member)
	{
		memberCache.add(member);
	}
	
	public void addImmigrant(long memberID)
	{
		immigrants.put(memberID, (long) (System.currentTimeMillis() + 6.048e+8));
	}
	
	public List<Poll> getPolls()
	{
		return polls;
	}
	
	public boolean isImmigrant(long id)
	{
		for(long sample : immigrants.keySet())
		{
			if(id == sample)
			{
				return true;
			}
		}
		
		return false;
	}
	
	public void removeMember(long memberID)
	{
		Iterator<Entry<Long, Long>> iterator = immigrants.entrySet().iterator();
		
		while(iterator.hasNext())
		{
			Entry<Long, Long> member = iterator.next();
			
			if(member.getKey() == memberID)
			{
				iterator.remove();
				return;
			}
		}
	}
	
	public List<ServerMember> getMembers()
	{
		return memberCache;
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
	
	public void tick(JDA jda)
	{
		for(Entry<Long, Long> entry : immigrants.entrySet())
		{
			if(System.currentTimeMillis() - entry.getValue() > 0)
			{
				Guild guild = jda.getGuildById(DMain.SERVER_ID);
				guild.removeRoleFromMember(guild.retrieveMemberById(entry.getKey()).complete(), DMain.IMMIGRANT).queue();
			}
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
				DMain.log("Opening up Presidential vote");
				
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
						
						Candidate nextPresident = candidates.get(0);
						int maxVotes = votes[0];
						
						DMain.log("Counting presidential votes! Candidate 1 = " + nextPresident.getID() + " " + maxVotes);
						
						for(int i = 1; i < candidates.size(); i++)
						{
							Candidate candidate = candidates.get(i);
							DMain.log(candidate.getID() + " " + votes[i]);
							
							if(votes[i] > maxVotes)
							{
								nextPresident = candidate;
								maxVotes = votes[i];
							}
						}
						
						candidates.clear();
						
						// President is elected
						DMain.log(nextPresident.getID() + " won");
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
							DMain.log("Same President " + presidentID);
							lastTerm = true;
						}
						else
						{
							DMain.log("Elected new president " + presidentID);
						}
						
						presidentID = nextPresident.getID();
						slogan = nextPresident.getSlogan();
						termEndTime = System.currentTimeMillis() + TERM_LENGTH;
						
						guild.getTextChannelById(DMain.VOTING_BOOTH).sendMessage("Welcome <@" + nextPresident.getID() + "> to The White House!").complete().delete().queueAfter(1, TimeUnit.HOURS);
						Member nextPresidentMember = guild.retrieveMemberById(nextPresident.getID()).complete();
						guild.addRoleToMember(nextPresidentMember, DMain.THE_PRESIDENT).complete();
						
						// New president
						this.presidentialCount++;
						
						// Add to commanders and queefs
						Role safeParty = Optional.ofNullable(guild.getRoleById(nextPresident.getRoleID())).orElse(DMain.THE_PRESIDENT);
						EmbedBuilder e = new EmbedBuilder();
						e.setTitle(ordinal(DMain.server.getPresidentialCount()) + " President of Discordias, " + nextPresidentMember.getUser().getGlobalName());
						e.setColor(safeParty.getColor());
						e.setDescription("<@" + nextPresidentMember.getId() + ">" + " of <@&" + safeParty.getIdLong() + ">\n\n*\"" + nextPresident.getSlogan() + "\"*");
						e.setImage(nextPresidentMember.getEffectiveAvatarUrl());
						e.setFooter("Served " + new SimpleDateFormat("MM/dd/yyyy").format(new Date(DMain.server.getTermEndTime() - Server.TERM_LENGTH)) + " - " + new SimpleDateFormat("MM/dd/yyyy").format(new Date(DMain.server.getTermEndTime())), jda.getSelfUser().getEffectiveAvatarUrl());
						jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.COMMANDERS_AND_QUEEFS).sendMessageEmbeds(e.build()).queue();
						
						// Update data
						DMain.updateServerData();
					}
				}
			}
		}
	}
	
	private static String ordinal(int i)
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
		e.setTitle(new SimpleDateFormat("MM/dd/yyyy").format(new Date()) + " Presidential Election");
		e.setColor(Color.red);
		
		StringBuilder builder = new StringBuilder("@everyone it's time. By the power of the people and the Magna Farta, we will elect our next monthly President that represents the core of this nation's beliefs and thereby representing the people. Cast your vote below:\n");
		
		// Sort candidates by their slot
		DMain.server.candidates.stream().sorted((o1, o2) -> Integer.compare(o1.getSlot(), o2.getSlot())).forEach((candidate) -> {
			builder.append("\n**#" + (candidate.getSlot() + 1) + ": <@" + candidate.getID() + ">** (<@&" + candidate.getRoleID() + ">) - *\"" + MarkdownSanitizer.sanitize(candidate.getSlogan()) + "\"*");
		});
		
		if(DMain.server.getCandidates().isEmpty())
			builder.append("\n*There are no active presidential candidates. Run for office with </campaign:" + DMain.getCommandByName("campaign").getId() + ">.");
		
		e.setImage("https://cdn.discordapp.com/app-icons/910579031391498330/c65afb3995baa1c31212e43f1f643e7e.png");
		e.setDescription(builder.toString());
		e.setFooter("Vote will be decided in " + (int) (Server.PRESIDENTIAL_VOTE_TIME / 3.6e+6) + " hours. Thank you for being an active participant in our perfect society.");
		return e.build();
	}
	
	/**
	 * Defaults / nulls President values and removes the role from the President.
	 */
	public Callable<Void> impeachPresident(Guild guild)
	{
		return new Callable<Void>()
		{
			@Override
			public Void call() throws Exception
			{
				DMain.log("Impeached President");
				// Reset data
				presidentID = 0;
				termEndTime = System.currentTimeMillis();
				
				// Remove presidential role
				guild.removeRoleFromMember(guild.retrieveMemberById(DMain.server.getPresidentID()).complete(), DMain.THE_PRESIDENT).complete();
				return null;
			}
		};
	}
	
	public Callable<Void> addAmendment(JDA jda, String content)
	{
		return new Callable<Void>()
		{
			@Override
			public Void call() throws Exception
			{
				amendmentIDs.add(jda.getTextChannelById(DMain.AMENDMENTS).sendMessage("**Amendment #" + (getAmendments() + 1) + "** - " + MarkdownSanitizer.sanitize(content)).complete().getId());
				return null;
			}
		};
	}
	
	public Callable<Void> repealAmendment(JDA jda, int number)
	{
		return new Callable<Void>()
		{
			@Override
			public Void call() throws Exception
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
				
				return null;
			}
		};
	}
	
	public String getAmendment(JDA jda, int index)
	{
		return jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(amendmentIDs.get(index)).complete().getContentRaw();
	}
	
	public void addSecret(String word, String response)
	{
		secretCommands.put(word.toLowerCase(), response);
	}
	
	public boolean removeSecret(String word)
	{
		return secretCommands.remove(word) != null;
	}
	
	public Map<String, String> getSecretCommands()
	{
		return secretCommands;
	}
	
	public List<Candidate> getCandidates()
	{
		return candidates;
	}
	
	@Override
	public String toString()
	{
//		try {
			return JsonUtils.serialize(this);
//		} catch(Throwable t) {
//			DMain.error("Failed to serialize server", t);
//			throw t;
//		}
		
//		StringBuilder builder = new StringBuilder(System.currentTimeMillis() + "\n\"" + slogan + "\" " + termEndTime + " " + getAmendments() + " " + lastTerm + "\n");
//		
//		// For each amendment
//		for(String sample : amendmentIDs)
//		{
//			builder.append(sample + "\n");
//		}
//		
//		// Append secret commands
//		ObjectNode node = WebUtils.createObjectNode();
//		secretCommands.forEach((key, value) -> node.put(key, value));
//		builder.append(node.toString() + "\n");
//		
//		// For each ServerMember
//		for(ServerMember member : members)
//		{
//			builder.append(member + "\n");
//		}
//		
//		String result = builder.toString();
//		return result.substring(0, result.length() - 1);
	}
	
	public String removeAmendment(JDA jda, int number)
	{
		if(number > 0 && number <= amendmentIDs.size())
		{
			// Decrement to indices
			number--;
			// Delete OG message
			jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(amendmentIDs.get(number)).complete().delete().queue();
			// Remove from data
			return "Deleted " + amendmentIDs.remove(number);
		}
		
		return "Did not find amendment with ID " + number;
	}
	
	public void updatePresidentialVote(JDA jda)
	{
		getPresidentialVote(jda).editMessageEmbeds(buildPresidentialVote()).complete();
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
			if(!found) return slot;
		}
		
		return -1;
	}
}
