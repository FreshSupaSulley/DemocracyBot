package democracy;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

public class Poll {
	
	private static final String YES_EMOJI = "U+2705", NO_EMOJI = "U+1f6ab";
	
	private TextChannel channel;
	private Callable<?>[] actions;
	private PollType type;
	private User pollFocusMember;
	
	private long messageID, messageTime, afterMessageTime;
	private Message afterMessage;
	
	private boolean decided;
	private String summary;
	
	public Poll(Color color, PollType type, String summary, String description, User pollFocusMember, TextChannel channel, Callable<?>... actions)
	{
		this.type = type;
		this.summary = summary;
		this.pollFocusMember = pollFocusMember;
		this.channel = channel;
		this.actions = actions;
		
		messageTime = System.currentTimeMillis();
		
		// Create message
		channel.sendMessageEmbeds(pollCreate(type, description, color)).queue(message ->
		{
			message.addReaction(Emoji.fromUnicode(YES_EMOJI)).queue();
			message.addReaction(Emoji.fromUnicode(NO_EMOJI)).queue();
			messageID = message.getIdLong();
		});
	}
	
	public boolean isComplete()
	{
		if(decided)
		{
			// After messages last 1 hour
			if(System.currentTimeMillis() - afterMessageTime > type.votingCooldown)
			{
				afterMessage.delete().queue();
				return true;
			}
		}
		else if(System.currentTimeMillis() - messageTime > type.votingWindow)
		{
			DMain.log("Poll \"" + summary + "\" has closed its voting window. Deciding...");
			
			decided = true;
			afterMessageTime = System.currentTimeMillis();
			
			Message message = null;
			
			try {
				message = channel.retrieveMessageById(messageID).complete();
			} catch(Exception e) {
				DMain.error("Could not retrieve original poll vote message");
				DMain.log(e);
				return true;
			}
			
			List<User> yesVoters = new ArrayList<User>();
			List<User> noVoters = new ArrayList<User>();
			
			// Add reactions
			for(MessageReaction r : message.getReactions())
			{
				EmojiUnion emoji = r.getEmoji();
				if(emoji.getType() == Emoji.Type.CUSTOM) continue;
				
				switch(emoji.asUnicode().getAsCodepoints())
				{
					case YES_EMOJI:
					{
						yesVoters = r.retrieveUsers().complete();
						break;
					}
					case NO_EMOJI:
					{
						noVoters = r.retrieveUsers().complete();
						break;
					}
				}
			}
			
			// Delete duplicates
			Iterator<User> yesIterator = yesVoters.iterator();
			while(yesIterator.hasNext())
			{
				User yesVoter = yesIterator.next();
				
				Iterator<User> noIterator = noVoters.iterator();
				while(noIterator.hasNext())
				{
					User noVoter = noIterator.next();
					
					if(yesVoter.getIdLong() == noVoter.getIdLong())
					{
						DMain.log("Duplicate voter: " + noVoter.getName());
						yesIterator.remove();
						noIterator.remove();
					}
				}
			}
			
			int numYes = yesVoters.size();
			int numNo = noVoters.size();
			
			DMain.log("To decide: Yes = " + numYes + ", No = " + numNo);
			
			// Delete voting message
			message.delete().queue();
			
			// Check for ratio
			if(type.passesPoll(numYes, numNo))
			{
				DMain.log("***" + type.name() + (pollFocusMember != null ? " <@" + pollFocusMember.getName() + ">" : "") + "*** poll (" + summary + ") passed!");
				afterMessage = channel.sendMessage("Poll ***" + type.name() + (pollFocusMember != null ? " @" + pollFocusMember.getName() : "") + "*** (" + summary + ") passed!").complete();
				
				// Perform actions
				for(Callable<?> action : actions)
				{
					try {
						action.call();
					} catch(Exception e) {
						DMain.error("Error running action during passed poll");
						DMain.log(e);
					}
				}
			}
			else
			{
				DMain.log("***" + type.name() + (pollFocusMember != null ? " <@" + pollFocusMember.getName() + ">" : "") + "*** poll (" + summary + ") failed to pass. Needs " + type.minParticipation + " voters and " + (int) (type.ratio * 100) + "% approval.");
				afterMessage = channel.sendMessage("Poll ***" + type.name() + (pollFocusMember != null ? " @" + pollFocusMember.getName() : "") + "*** (" + summary + ") failed to pass. Needs " + type.minParticipation + " voters and " + (int) (type.ratio * 100) + "% approval.").complete();
			}
		}
		
		return false;
	}
	
	public boolean isDecided()
	{
		return decided;
	}
	
	public PollType getType()
	{
		return type;
	}
	
	public long getFocusMemberID()
	{
		return pollFocusMember.getIdLong();
	}
	
	public long getMessageID()
	{
		return messageID;
	}
	
	private MessageEmbed pollCreate(PollType type, String description, Color color)
	{
		EmbedBuilder e = new EmbedBuilder();
		e.setTitle(DMain.BOT_NAME + " Vote");
		e.setDescription(description);
		e.setColor(color);
		
		e.setFooter("Requires " + (int) (type.ratio * 100) + "% majority and " + type.minParticipation + " minimum voters. Decision in " + type.votingTimeParsed + ".");
		return e.build();
	}
	
	public enum PollType {
		
		VIOLATION(0.51f, 2, 60000, 60000),
		IMPEACH(0.75f, 7, 86400000, 259200000),
		PROPOSE(0.51f, 3, 3600000, 43200000),
		ARCHIVE(0.65f, 3, 3600000, 43200000),
		REPEAL(0.51f, 3, 3600000, 43200000),
		CREATE_PARTY(0.35f, 2, 3600000, 600000);
		
		private float ratio;
		private int minParticipation;
		private long votingWindow, votingCooldown;
		private String votingTimeParsed;
		
		private PollType(float ratio, int minParticipation, long votingWindow, long votingCooldown)
		{
			this.ratio = ratio;
			this.minParticipation = minParticipation;
			this.votingWindow = votingWindow;
			this.votingCooldown = votingCooldown;
			
			// Parse voting times
			String unit = "hour";
			double time = votingWindow / 3600000D;
			
			if(time < 1)
			{
				unit = "minute";
				time = votingWindow / 60000;
			}
			
			votingTimeParsed = (int) time + " " + unit + ((int) time != 1 ? "s" : "");
		}
		
		public boolean passesPoll(int numYes, int numNo)
		{
			if(numYes + numNo < minParticipation) return false;
			
			if(numYes * 1f / (numYes + numNo) >= ratio) return true;
			return false;
		}
		
		public int getMinParticipants()
		{
			return minParticipation;
		}
		
		public long getVotingWindow()
		{
			return votingWindow;
		}
		
		public long getVotingCooldown()
		{
			return votingCooldown;
		}
		
		public String getVotingTimeParsed()
		{
			return votingTimeParsed;
		}
	}
}
