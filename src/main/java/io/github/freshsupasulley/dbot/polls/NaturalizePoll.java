package io.github.freshsupasulley.dbot.polls;

import io.github.freshsupasulley.dbot.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class NaturalizePoll extends Poll<NaturalizePoll> {
	
	private final long member;
	
	public NaturalizePoll(TextChannel channel, Member member)
	{
		// 3 day cooldown
		super(0.75f, 5, 259200000L, "Naturalize " + member.getEffectiveName() + "? They will be able to participate in democracy. If failed, they cannot be renaturalized.", channel);
		
		this.member = member.getIdLong();
	}
	
	@Override
	public boolean isDuplicate(NaturalizePoll poll)
	{
		return member == poll.member;
	}
	
	@Override
	protected void pollPassed(JDA jda)
	{
		Guild guild = jda.getGuildById(Main.SERVER_ID);
		guild.retrieveMemberById(member).queue(result ->
		{
			Main.server.naturalize(result);
		}, e ->
		{
			Main.log.error("Failed to find member to naturalize while running naturalization poll", e);
		});
	}
	
	@Override
	protected void pollFailed(JDA jda)
	{
		Main.server.addToCitizenBlacklist(member);
	}
}
