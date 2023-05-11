package democracy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.supasulley.web.WebUtils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;

public class Server {
	
	// Members
	private ArrayList<ServerMember> members;
	// <End time, UserID>
	private Map<Long, Long> immigrants;
	
	// The President
	private long presidentID;
	private String slogan;
	// Term length is 30 days
	private long termLength = 2592000000L, termEndTime;
	private int amendments = 0;
	private ArrayList<String> messageIDs;
	
	// Secret command
	private HashMap<String, String> secretCommands;
	
	private boolean lastTerm;
	
	public Server(long presidentID, String slogan, long termEndTime, int amendments, ArrayList<ServerMember> members, ArrayList<String> messageIDs, HashMap<String, String> secretCommands, boolean lastTerm)
	{
		immigrants = new HashMap<Long, Long>();
		this.presidentID = presidentID;
		this.slogan = slogan;
		this.termEndTime = termEndTime;
		this.amendments = amendments;
		this.members = members;
		this.messageIDs = messageIDs;
		this.secretCommands = secretCommands;
		this.lastTerm = lastTerm;
	}
	
	public boolean isPresident(ServerMember member)
	{
		if(!hasPresident()) return false;
		if(member.getID() == presidentID) return true;
		return false;
	}
	
	public void addMember(ServerMember member)
	{
		members.add(member);
	}
	
	public void addImmigrant(long memberID)
	{
		immigrants.put(memberID, (long) (System.currentTimeMillis() + 6.048e+8));
	}
	
	public boolean isImmigrant(long id)
	{
		for(long sample : immigrants.keySet())
		{
			if(id == sample)
			{
				return true;
			}
		}
		
		return false;
	}
	
	public void removeMember(long memberID)
	{
		Iterator<Entry<Long, Long>> iterator = immigrants.entrySet().iterator();
		
		while(iterator.hasNext())
		{
			Entry<Long, Long> member = iterator.next();
			
			if(member.getKey() == memberID)
			{
				iterator.remove();
				return;
			}
		}
	}
	
	public ArrayList<ServerMember> getMembers()
	{
		return members;
	}
	
	public long getPresidentID()
	{
		return presidentID;
	}
	
	public boolean hasPresident()
	{
		return presidentID != 0;
	}
	
	public boolean isLastTerm()
	{
		return lastTerm;
	}
	
	public long millsRemainingInTerm()
	{
		return termEndTime - System.currentTimeMillis();
	}
	
	public int getAmendments()
	{
		return amendments;
	}
	
	public String getPresidentialSlogan()
	{
		return slogan;
	}
	
	public void tick(JDA jda)
	{
		for(Entry<Long, Long> entry : immigrants.entrySet())
		{
			if(System.currentTimeMillis() - entry.getValue() > 0)
			{
				Guild guild = jda.getGuildById(DMain.SERVER_ID);
				guild.removeRoleFromMember(guild.retrieveMemberById(entry.getKey()).complete(), DMain.IMMIGRANT).queue();
			}
		}
	}
	
	/**
	 * We use the callable interface to run an action later. We return the president value, which is set to null, once this method is called.
	 */
	public Callable<Void> impeachPresident(Guild guild)
	{
		return new Callable<Void>()
		{
			@Override
			public Void call() throws Exception
			{
				DMain.log("Impeached President");
				// Reset data
				presidentID = 0;
				termEndTime = System.currentTimeMillis();
				
				// Remove presidential role
				guild.removeRoleFromMember(guild.retrieveMemberById(DMain.server.getPresidentID()).complete(), DMain.THE_PRESIDENT).complete();
				return null;
			}
		};
	}
	
	public Callable<Void> addAmendment(JDA jda, String content)
	{
		return new Callable<Void>()
		{
			@Override
			public Void call() throws Exception
			{
				messageIDs.add(jda.getTextChannelById(DMain.AMENDMENTS).sendMessage("**Amendment #" + ++amendments + "** - " + content).complete().getId());
				return null;
			}
		};
	}
	
	public Callable<Void> repealAmendment(JDA jda, int number)
	{
		return new Callable<Void>()
		{
			@Override
			public Void call() throws Exception
			{
				Message message = jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(messageIDs.get(number)).complete();
				String raw = message.getContentRaw();
				
				if(!raw.startsWith("~~") && !raw.endsWith("~~"))
				{
					message.editMessage("~~" + message.getContentRaw() + "~~").complete();
				}
				else
				{
					message.editMessage(raw.substring(2, raw.length() - 2)).complete();
				}
				
				return null;
			}
		};
	}
	
	public void electPresident(Candidate president)
	{
		if(presidentID == president.getID())
		{
			DMain.log("Same President " + presidentID);
			lastTerm = true;
		}
		else
		{
			DMain.log("Elected new president " + presidentID);
		}
		
		presidentID = president.getID();
		slogan = president.getSlogan();
		termEndTime = System.currentTimeMillis() + termLength;
	}
	
	public String getAmendment(JDA jda, int number)
	{
		return jda.getTextChannelById(DMain.AMENDMENTS).retrieveMessageById(messageIDs.get(number)).complete().getContentRaw();
	}
	
	public void addSecret(String word, String response)
	{
		secretCommands.put(word.toLowerCase(), response);
	}
	
	public boolean removeSecret(String word)
	{
		return secretCommands.remove(word) != null;
	}
	
	public HashMap<String, String> getSecretCommands()
	{
		return secretCommands;
	}
	
	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder(System.currentTimeMillis() + "\n\"" + slogan + "\" " + termEndTime + " " + amendments + " " + lastTerm + "\n");
		
		// For each amendment
		for(String sample : messageIDs)
		{
			builder.append(sample + "\n");
		}
		
		// Append secret commands
		ObjectNode node = WebUtils.createObjectNode();
		secretCommands.forEach((key, value) -> node.put(key, value));
		builder.append(node.toString() + "\n");
		
		// For each ServerMember
		for(ServerMember member : members)
		{
			builder.append(member + "\n");
		}
		
		String result = builder.toString();
		return result.substring(0, result.length() - 1);
	}
}
