package io.github.freshsupasulley.dbot;

import java.util.HashSet;
import java.util.Set;

import net.dv8tion.jda.api.entities.Member;

// damn records aren't a thing in this java version :(
// getters and setters it is
public class PoliticalParty {
	
	private final long role, category;
	
	private Set<Long> blacklist = new HashSet<Long>();
	
	/** Leaders can change */
	private long leader;
	
	public PoliticalParty(long role, long category, long owner)
	{
		this.role = role;
		this.category = category;
		this.leader = owner;
	}
	
	/**
	 * Checks if the member is banned.
	 * 
	 * @return true if the member is banned, false otherwise
	 */
	public boolean isBanned(Member member)
	{
		return blacklist.contains(member.getIdLong());
	}
	
	/**
	 * Gets the ban list of the party.
	 * 
	 * @return list of all banned users
	 */
	public Set<Long> getBlacklist()
	{
		return blacklist;
	}
	
	/**
	 * Bans a member from this party.
	 * 
	 * @param member member
	 * @return true if the member was banned, false if already banned
	 */
	public boolean addBan(Member member)
	{
		return blacklist.add(member.getIdLong());
	}
	
	/**
	 * Unbans a member from this party.
	 * 
	 * @param member member
	 * @return true if the member was unbanned, false if was never banned
	 */
	public boolean removeBan(Member member)
	{
		return blacklist.remove(member.getIdLong());
	}
	
	public long getRole()
	{
		return role;
	}
	
	public long getCategory()
	{
		return category;
	}
	
	public long getLeaderID()
	{
		return leader;
	}
	
	public void setLeader(long leader)
	{
		this.leader = leader;
	}
	
	public boolean equals(PoliticalParty party)
	{
		return party != null && party.role == this.role;
	}
}
