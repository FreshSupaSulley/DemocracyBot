package democracy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Used to track the proposal times of each member.
 */
public class ServerMember {
	
	private final long userID;
	
	// Times of proposing each type of poll
	private Map<String, Long> pollCooldownExpiryTimes = new HashMap<String, Long>();
	
	public ServerMember(long userID)
	{
		this.userID = userID;
	}
	
	/**
	 * Ticks this server member to help clear cache.
	 * 
	 * @return true if this member should be removed from cache, false otherwise.
	 */
	public boolean shouldDelete()
	{
		Iterator<Map.Entry<String, Long>> iterator = pollCooldownExpiryTimes.entrySet().iterator();
		boolean updated = true;
		
		while(iterator.hasNext())
		{
			// If the expiry time hasn't been passed yet
			if(iterator.next().getValue() > System.currentTimeMillis())
			{
				updated = false;
				break;
			}
		}
		
		if(updated)
		{
			DMain.log.info("Clearing poll cooldown expiry times for {}", userID);
			pollCooldownExpiryTimes.clear();
		}
		
		return updated;
	}
	
	/**
	 * Ensure this person isn't endlessly requesting this poll. If the member can propose, the cooldown is reset.
	 * 
	 * @param poll poll to check
	 * @return true if the member can propose the poll, false otherwise
	 */
	public boolean canPropose(Poll poll)
	{
		// Hehe (I need this for when I need to fix bot)
		if(userID == DMain.OWNER_ID) return true;
		
		// If the current time passed the expiry time
		if(System.currentTimeMillis() >= pollCooldownExpiryTimes.getOrDefault(poll.getClass().getName(), 0L))
		{
			pollCooldownExpiryTimes.put(poll.getClass().getName(), System.currentTimeMillis() + poll.getVotingCooldown());
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
		return pollCooldownExpiryTimes.getOrDefault(poll.getClass().getName(), System.currentTimeMillis()) - System.currentTimeMillis();
	}
	
	/**
	 * @return Discord user ID
	 */
	public long getID()
	{
		return userID;
	}
}
