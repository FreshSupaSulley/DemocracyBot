package democracy;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ProposePoll extends Poll {
	
	private final String proposal;
	
	public ProposePoll(TextChannel channel, String proposal)
	{
		super(0.5f, 5, 43200000, "New amendment: " + proposal, channel);
		this.proposal = proposal;
	}
	
	@Override
	protected void performAction(JDA jda)
	{
		DMain.server.addAmendment(jda, proposal);
	}
	
	@Override
	protected String getFancyName()
	{
		return "Propose";
	}
}
