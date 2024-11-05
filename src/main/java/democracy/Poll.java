package democracy;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.entities.messages.MessagePoll.Answer;
import net.dv8tion.jda.api.utils.messages.MessagePollData;

public class Poll {
	
	private static final String YES_EMOJI = "U+2705", NO_EMOJI = "U+1f6ab";
	
	private TextChannel channel;
	private Callable<?>[] actions;
	private PollType type;
	private User pollFocusMember;
	
	private long messageID, startTime;
	private Message afterMessage;
	
	private String question;
	
	public Poll(PollType type, String question, User pollFocusMember, TextChannel channel, Callable<?>... actions)
	{
		this.type = type;
		this.question = question;
		this.pollFocusMember = pollFocusMember;
		this.channel = channel;
		this.actions = actions;
		
		// Create message
		channel.sendMessagePoll(generatePoll()).queue(message ->
		{
			messageID = message.getIdLong();
			startTime = System.currentTimeMillis();
		});
	}
	
	private MessagePollData generatePoll()
	{
		// Make polls last a day not an hour
		return MessagePollData.builder(question.substring(0, Math.min(question.length(), MessagePoll.MAX_QUESTION_TEXT_LENGTH))).setDuration(1, TimeUnit.DAYS).addAnswer("Yes", Emoji.fromFormatted(YES_EMOJI)).addAnswer("No", Emoji.fromFormatted(NO_EMOJI)).build();
	}
	
	public void endPoll(Message message)
	{
		MessagePoll poll = message.getPoll();
		int numYes = 0;
		int numNo = 0;
		
		for(Answer answer : poll.getAnswers())
		{
			if(answer.getText().equals("Yes"))
			{
				numYes++;
			}
			else
			{
				numNo++;
			}
		}
		
		DMain.log("To decide: Yes = " + numYes + ", No = " + numNo);
		
		// Delete voting message
		message.delete().queue();
		
		// Check for ratio
		if(type.passesPoll(numYes, numNo))
		{
			DMain.log("***" + type.name() + (pollFocusMember != null ? " <@" + pollFocusMember.getName() + ">" : "") + "*** poll (" + question + ") passed!");
			afterMessage = channel.sendMessage("Poll ***" + type.name() + (pollFocusMember != null ? " @" + pollFocusMember.getName() : "") + "*** (" + question + ") passed!").complete();
			
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
			DMain.log("***" + type.name() + (pollFocusMember != null ? " <@" + pollFocusMember.getName() + ">" : "") + "*** poll (" + question + ") failed to pass. Needs " + type.minParticipation + " voters and " + (int) (type.ratio * 100) + "% approval.");
			afterMessage = channel.sendMessage("Poll ***" + type.name() + (pollFocusMember != null ? " @" + pollFocusMember.getName() : "") + "*** (" + question + ") failed to pass. Needs " + type.minParticipation + " voters and " + (int) (type.ratio * 100) + "% approval.").complete();
		}
		
		afterMessage.delete().queueAfter(1, TimeUnit.HOURS);
	}
	
	public long getMessageID()
	{
		return messageID;
	}
	
	public long getStartTime()
	{
		return startTime;
	}
	
	public enum PollType {
		
		PROPOSE(0.51f, 3, 43200000),
		REPEAL(0.51f, 3, 43200000);
		
		private float ratio;
		private int minParticipation;
		private long votingCooldown;
		
		private PollType(float ratio, int minParticipation, long votingCooldown)
		{
			this.ratio = ratio;
			this.minParticipation = minParticipation;
			this.votingCooldown = votingCooldown;
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
		
		public long getVotingCooldown()
		{
			return votingCooldown;
		}
	}
}
