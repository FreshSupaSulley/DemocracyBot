package io.github.freshsupasulley.dbot.polls;

import io.github.freshsupasulley.dbot.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ImpeachPoll extends Poll<ImpeachPoll> {
	
	public ImpeachPoll(TextChannel channel, String reason)
	{
		// 1 week cooldown
		super(0.75f, 5, 604800000L, "Impeach " + channel.getJDA().getGuildById(Main.SERVER_ID).retrieveMemberById(Main.server.getPresidentID()).complete().getUser().getName() + "? " + reason, channel);
	}
	
	// Don't allow 2 impeach polls
	@Override
	public boolean isDuplicate(ImpeachPoll poll)
	{
		return true;
	}
	
	@Override
	protected void pollPassed(JDA jda)
	{
		Main.server.impeachPresident(jda.getGuildById(Main.SERVER_ID));
	}
}
