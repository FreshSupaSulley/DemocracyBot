package democracy;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public abstract class CustomListener extends ListenerAdapter {
	
	@Override
	public final void onMessageReceived(MessageReceivedEvent event)
	{
		// Public commands
		if(event.getChannelType().isGuild())
		{
			onGuildMessageReceived(event);
		}
		// Private commands
		else if(event.isFromType(ChannelType.PRIVATE))
		{
			onPrivateMessageReceived(event);
		}
		else
		{
			DMain.log.warn("Message received, not guild or private message?", event);
		}
	}
	
	public abstract void onGuildMessageReceived(MessageReceivedEvent event);
	public abstract void onPrivateMessageReceived(MessageReceivedEvent event);
}
