package io.github.freshsupasulley.dbot;

import javax.annotation.Nullable;

import io.github.freshsupasulley.dbot.polls.Poll;

/**
 * Used to track the proposal times of each member.
 */
public class ServerMember {
	
	private final long userID;
	
	/** Corresponds to a role ID of their political party */
	private long partyRole;
	
	public ServerMember(long userID)
	{
		this.userID = userID;
	}
	
	/**
	 * To create copies of server members, for candidates.
	 * 
	 * @param member server member
	 */
	protected ServerMember(ServerMember member)
	{
		this.userID = member.userID;
		this.partyRole = member.partyRole;
	}
	
	/**
	 * Gets the political party this user is a member of.
	 * 
	 * @return the political party, or null if user is not apart of a party.
	 */
	@Nullable
	public PoliticalParty getPoliticalParty()
	{
		return Main.server.getParty(partyRole);
	}
	
	/**
	 * Sets the political party this member belongs to.
	 * 
	 * @param role political party role, or null to leave the party
	 */
	public void setPoliticalParty(@Nullable PoliticalParty party)
	{
		this.partyRole = party == null ? 0 : party.getRole();
	}
	
	/**
	 * Ensure this person isn't endlessly requesting this poll. If the member can propose, the cooldown is reset.
	 * 
	 * @param poll poll to check
	 * @return true if the member can propose the poll, false otherwise
	 */
	public boolean canPropose(Poll<?> poll)
	{
		return Main.server.meetsCooldown(this, poll);
	}
	
	/**
	 * Returns the time remaining in the poll cooldown.
	 * 
	 * @param poll poll to check
	 * @return time remaining in milliseconds before the member can request again
	 */
	public long getMillisRemaining(Poll<?> poll)
	{
		return Main.server.getMillisRemaining(this, poll);
	}
	
	/**
	 * @return Discord user ID
	 */
	public long getID()
	{
		return userID;
	}
}
