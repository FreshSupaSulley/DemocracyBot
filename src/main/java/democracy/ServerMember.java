package democracy;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to track the proposal times of each member.
 */
public class ServerMember {
	
	// 1 week cache time
	private static final long CACHE_TIME = 604800000L;
	
	private final long userID;
	private long deletionTime;
	
	// Times of proposing each type of poll
	private Map<String, Long> pollProposalTimes = new HashMap<String, Long>();
	
	public ServerMember(long userID)
	{
		this.userID = userID;
	}
	
	/**
	 * Ensure this person isn't endlessly requesting this poll.
	 * 
	 * @param poll poll to check
	 * @return true if the member can propose the poll, false otherwise
	 */
	public boolean canPropose(Poll poll)
	{
		if(System.currentTimeMillis() - pollProposalTimes.getOrDefault(poll.getClass().getName(), 0L) > poll.getVotingCooldown())
		{
			pollProposalTimes.put(poll.getClass().getName(), System.currentTimeMillis());
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns the time remaining in the poll cooldown.
	 * 
	 * @param poll poll to check
	 * @return time remaining in milliseconds before the member can request again
	 */
	public long getMillisRemaining(Poll poll)
	{
		return poll.getVotingCooldown() - (System.currentTimeMillis() - pollProposalTimes.getOrDefault(poll.getClass().getName(), System.currentTimeMillis()));
	}
	
	/**
	 * @return Discord user ID
	 */
	public long getID()
	{
		return userID;
	}
	
	/**
	 * Marks this member for deletion after they've left the server. Recalling this method simply delays when their data is deleted.
	 */
	public void markForDeletion()
	{
		deletionTime = System.currentTimeMillis() + CACHE_TIME;
	}
	
	/**
	 * @return true if the cache time for this member has expired
	 */
	public boolean shouldDelete()
	{
		return deletionTime != 0 && System.currentTimeMillis() > deletionTime;
	}
}
