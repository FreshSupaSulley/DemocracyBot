package democracy;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class RepealPoll extends Poll {
	
	private final int amendment;
	
	public RepealPoll(TextChannel channel, int amendment)
	{
		super(0.5f, 3, 43200000, "Repeal Amendment #" + (amendment + 1) + ": \"" + DMain.server.getAmendment(channel.getJDA(), amendment) + "\"", channel);
		this.amendment = amendment;
	}
	
	@Override
	protected void performAction(JDA jda)
	{
		DMain.server.repealAmendment(jda, amendment);
	}
}
