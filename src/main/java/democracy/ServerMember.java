package democracy;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to track the proposal times of each member.
 */
public class ServerMember {
	
	private final long userID;
	
	// Times of proposing each type of poll
	private Map<Class<? extends Poll>, Long> pollProposalTimes = new HashMap<Class<? extends Poll>, Long>();
	
	public ServerMember(long userID)
	{
		this.userID = userID;
	}
	
	/**
	 * Ensure this person isn't endlessly requesting this poll
	 * @param member
	 * @param typeNee
	 * @return
	 */
	public boolean canPropose(Poll poll)
	{
		if(System.currentTimeMillis() - pollProposalTimes.getOrDefault(poll.getClass(), 0L) > poll.getVotingCooldown())
		{
			pollProposalTimes.put(poll.getClass(), System.currentTimeMillis());
			return true;
		}
		
		return false;
	}
	
	public boolean isUser(long id)
	{
		return id == userID;
	}
	
	public long getID()
	{
		return userID;
	}
}
