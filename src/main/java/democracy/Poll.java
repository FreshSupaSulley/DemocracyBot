package democracy;

import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.entities.messages.MessagePoll.Answer;
import net.dv8tion.jda.api.utils.messages.MessagePollData;

/**
 * Uses officially supported Discord polling functions. All of these kinds of polls last an hour.
 */
public abstract class Poll {
	
	private static final String YES_EMOJI = "U+2705", NO_EMOJI = "U+1f6ab";
	
	// Serialize everything
	private String question;
	private long messageID, startTime;
	private float ratio;
	private int minParticipation;
	private long votingCooldown;
	
	public Poll(float ratio, int minParticipation, long votingCooldown, String question, TextChannel channel)
	{
		this.question = question;
		
		// Create message
		channel.sendMessagePoll(generatePoll()).queue(message ->
		{
			startTime = System.currentTimeMillis();
			messageID = message.getIdLong();
		});
	}
	
	private MessagePollData generatePoll()
	{
		return MessagePollData.builder(question.substring(0, Math.min(question.length(), MessagePoll.MAX_QUESTION_TEXT_LENGTH))).setDuration(1, TimeUnit.DAYS).addAnswer("Yes", Emoji.fromFormatted(YES_EMOJI)).addAnswer("No", Emoji.fromFormatted(NO_EMOJI)).build();
	}
	
	private boolean passesPoll(int numYes, int numNo)
	{
		// Ignore if min participation wasn't met
		if(numYes + numNo <= minParticipation) return false;
		return numYes * 1f / (numYes + numNo) > ratio;
	}
	
	protected abstract void performAction(JDA jda);
	
	/**
	 * Counts all votes and runs the associated poll action if passed.
	 * 
	 * @param jda     jda instance
	 */
	public void endPoll(JDA jda)
	{
		Message pollMessage = jda.getGuildById(DMain.SERVER_ID).getTextChannelById(DMain.VOTING_BOOTH).retrieveMessageById(messageID).complete();
		MessagePoll poll = pollMessage.getPoll();
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
		pollMessage.delete().queue();
		
		// Check for ratio
		if(passesPoll(numYes, numNo))
		{
			DMain.log("***" + this.getClass().getName() + "*** poll (" + question + ") passed!");
//			afterMessage = channel.sendMessage("Poll ***" + type.name() + (pollFocusMember != null ? " @" + pollFocusMember.getName() : "") + "*** (" + question + ") passed!").complete();
			
			// Perform actions
			try {
				performAction(jda);
			} catch(Throwable t) {
				DMain.error("Error running action during passed poll");
				DMain.log(t);
			}
		}
		else
		{
			// Fancy polling does this for us
			DMain.log("***" + this.getClass().getName() + "*** poll (" + question + ") failed to pass. Needs " + minParticipation + " voters and " + (int) (ratio * 100) + "% approval.");
//			afterMessage = channel.sendMessage("Poll ***" + type.name() + (pollFocusMember != null ? " @" + pollFocusMember.getName() : "") + "*** (" + question + ") failed to pass. Needs " + type.minParticipation + " voters and " + (int) (type.ratio * 100) + "% approval.").complete();
		}
		
//		afterMessage.delete().queueAfter(1, TimeUnit.HOURS);
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
