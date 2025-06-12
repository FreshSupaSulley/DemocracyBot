package democracy;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class RepealPoll extends Poll {
	
	private final int amendment;
	
	/**
	 * Creates a new repeal amendment poll.
	 * 
	 * @param channel   vote proposal
	 * @param amendment amendment index, <b>NOT</b> the number
	 */
	public RepealPoll(TextChannel channel, int amendment)
	{
		super(0.5f, 3, 43200000, "Repeal " + MarkdownSanitizer.sanitize(DMain.server.getAmendment(channel.getJDA(), amendment - 1)), channel);
		this.amendment = amendment;
	}
	
	@Override
	protected void performAction(JDA jda)
	{
		DMain.server.repealAmendment(jda, amendment);
	}
	
	@Override
	protected String getFancyName()
	{
		return "Repeal";
	}
}
