package democracy;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ImpeachPoll extends Poll {
	
	public ImpeachPoll(TextChannel channel, String reason)
	{
		// 1 week cooldown
		super(0.75f, 5, 604800000L, "Impeach " + channel.getJDA().getGuildById(DMain.SERVER_ID).retrieveMemberById(DMain.server.getPresidentID()).complete().getUser().getGlobalName() + "? " + reason, channel);
	}
	
	@Override
	protected void performAction(JDA jda)
	{
		DMain.server.impeachPresident(jda.getGuildById(DMain.SERVER_ID));
	}
	
	@Override
	protected String getFancyName()
	{
		return "Impeach";
	}
}
