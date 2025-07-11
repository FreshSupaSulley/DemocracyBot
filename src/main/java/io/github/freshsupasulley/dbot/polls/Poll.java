package io.github.freshsupasulley.dbot.polls;

import java.time.Duration;
import java.util.function.Consumer;

import io.github.freshsupasulley.dbot.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.utils.messages.MessagePollData;

/**
 * Uses officially supported Discord polling functions. All of these kinds of polls last an hour.
 */
public abstract class Poll<T extends Poll<T>> {
	
	/** Defines the maximum allowed characters a poll can have before content is added. Not enforced */
	public static final int POLL_QUESTION_PREFIX = 50;
	
	private static final String YES_EMOJI = "U+2705", NO_EMOJI = "U+1f6ab";
	
	// Serialize everything
	private String question;
	private long messageID, startTime;
	private float ratio;
	private int minParticipation;
	private long votingCooldown;
	
	// Don't store
	private transient TextChannel channel;
	
	public Poll(float ratio, int minParticipation, long votingCooldown, String question, TextChannel channel)
	{
		this.ratio = ratio;
		this.minParticipation = minParticipation;
		this.votingCooldown = votingCooldown;
		this.question = question;
		this.channel = channel;
	}
	
	/**
	 * Checks if this poll equals another poll.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public final boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null || getClass() != obj.getClass())
			return false;
		
		return isDuplicate((T) obj);
	}
	
	/**
	 * Checks if the poll is a duplicate of another poll.
	 * 
	 * @param poll poll to check
	 * @return true if this poll is a duplicate, false otherwise
	 */
	public abstract boolean isDuplicate(T poll);
	
	public void firePoll(Consumer<Message> onSuccess, Consumer<Throwable> onFailure)
	{
		// Create message
		channel.sendMessagePoll(generatePoll()).queue(message ->
		{
			startTime = System.currentTimeMillis();
			messageID = message.getIdLong();
			onSuccess.accept(message);
		}, onFailure);
	}
	
	private MessagePollData generatePoll()
	{
		if(question.length() > MessagePoll.MAX_QUESTION_TEXT_LENGTH)
		{
			Main.log.error("{} has too long of a question", this);
		}
		
		return MessagePollData.builder(question.substring(0, Math.min(question.length(), MessagePoll.MAX_QUESTION_TEXT_LENGTH))).setDuration(getVoteTime()).addAnswer("Yes", Emoji.fromFormatted(YES_EMOJI)).addAnswer("No", Emoji.fromFormatted(NO_EMOJI)).build();
	}
	
	/**
	 * @return universal voting time for polls
	 */
	public Duration getVoteTime()
	{
		return Duration.ofDays(1);
	}
	
	private boolean passesPoll(int numYes, int numNo)
	{
		// Ignore if min participation wasn't met
		if(numYes + numNo <= minParticipation)
			return false;
		return numYes * 1f / (numYes + numNo) > ratio;
	}
	
	protected abstract void pollPassed(JDA jda);
	
	protected void pollFailed(JDA jda)
	{
	}
	
	/**
	 * Counts all votes and runs the associated poll action if passed.
	 * 
	 * <p>
	 * Ideally, this should check for naturalized citizens only but we're hiding the voting-booth channel from immigrants so this still should work.
	 * </p>
	 * 
	 * @param jda jda instance
	 */
	public void endPoll(JDA jda)
	{
		// Refresh channel
		channel = jda.getGuildById(Main.SERVER_ID).getTextChannelById(Main.VOTING_BOOTH);
		
		Message pollMessage = channel.retrieveMessageById(messageID).onErrorMap(t ->
		{
			Main.log.error("Failed to retrieve poll message", t);
			return null;
		}).complete();
		
		// Abandon if poll message couldn't get found
		if(pollMessage == null)
			return;
		
		MessagePoll poll = pollMessage.getPoll();
		// Due to the ordering when creating the poll, yes is always the first, no is always the second
		int numYes = poll.getAnswers().get(0).getVotes();
		int numNo = poll.getAnswers().get(1).getVotes();
		
		Main.log.info("To decide: Yes = " + numYes + ", No = " + numNo);
		
		// Delete voting message
		pollMessage.delete().queue();
		// Message afterMessage = null;
		
		// Check for ratio
		if(passesPoll(numYes, numNo))
		{
			Main.log.info("{} poll ({}) passed!", this, question);
			channel.sendMessage("**" + question + "** passed, with a Yes / No ratio of **" + numYes + "** / **" + numNo + "**!").complete();
			
			// Perform actions
			try
			{
				pollPassed(jda);
				Main.updateServerData();
			} catch(Exception e)
			{
				Main.log.error("Error running action during passed poll", e);
			}
		}
		else
		{
			// Fancy polling does this for us
			String failedMsg = "**" + question + "** failed to pass. Needs " + minParticipation + " voters and " + (int) (ratio * 100) + "% approval (Yes / No ratio: **" + numYes + "** / **" + numNo + "**)";
			channel.sendMessage(failedMsg).complete();
			Main.log.info(failedMsg);
			
			// Perform actions
			try
			{
				pollFailed(jda);
				Main.updateServerData();
			} catch(Exception e)
			{
				Main.log.error("Error running action during failed poll", e);
			}
		}
		
		// Matches Server's checkMessageForPollResult function
		// Idk how to check if its an Unknown Message
		// This is picked up by checkPolLResult function
		// afterMessage.delete().queueAfter(1, TimeUnit.HOURS, success -> {}, failure ->
		// {
		// DMain.log.error("Failed to delete poll after message", failure);
		// });
	}
	
	public long getMessageID()
	{
		return messageID;
	}
	
	public int getMinParticipants()
	{
		return minParticipation;
	}
	
	public long getVotingCooldown()
	{
		return votingCooldown;
	}
	
	public long getStartTime()
	{
		return startTime;
	}
}
