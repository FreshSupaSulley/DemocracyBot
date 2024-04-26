package democracy;

import democracy.Poll.PollType;

public class ServerMember {
	
	private String name;
	
	private final long userID;
	
	// Times of proposing each type of poll
	private long[] pollProposalTimes = new long[PollType.values().length];
	
	public ServerMember(String name, long userID)
	{
		this.name = name;
		this.userID = userID;
	}
	
	public void update(String name)
	{
		this.name = name;
	}
	
	/**
	 * Ensure this person isn't endlessly requesting this poll
	 * @param member
	 * @param typeNee
	 * @return
	 */
	public boolean canPropose(PollType type)
	{
		for(int i = 0; i < PollType.values().length; i++)
		{
			if(type == PollType.values()[i])
			{
				if(System.currentTimeMillis() - pollProposalTimes[i] > type.getVotingCooldown())
				{
					pollProposalTimes[i] = System.currentTimeMillis();
					return true;
				}
				
				return false;
			}
		}
		
		System.err.println("Unknown PollType " + type);
		return false;
	}
	
	public boolean isUser(long id)
	{
		return id == userID;
	}
	
	public String getName()
	{
		return name;
	}
	
	public long getID()
	{
		return userID;
	}
	
	@Override
	public String toString()
	{
		return "\"" + name + "\"," + userID + ",";
	}
}
