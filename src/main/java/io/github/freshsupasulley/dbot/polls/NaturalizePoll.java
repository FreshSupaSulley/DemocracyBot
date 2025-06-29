package io.github.freshsupasulley.dbot.polls;

import io.github.freshsupasulley.dbot.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class NaturalizePoll extends Poll {
	
	private final long member;
	
	public NaturalizePoll(TextChannel channel, Member member)
	{
		super(0.5f, 5, 43200000, "Naturalize " + member.getEffectiveName() + "? They will be able to propose and participate in democracy", channel);
		
		this.member = member.getIdLong();
	}
	
	@Override
	protected void performAction(JDA jda)
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
}
