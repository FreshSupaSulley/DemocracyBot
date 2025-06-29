package io.github.freshsupasulley.dbot;

// damn records aren't a thing in this java version :(
// getters and setters it is
public class PoliticalParty {
	
	private final long role, category;
	
	/** Leaders can change */
	private long leader;
	
	public PoliticalParty(long role, long category, long owner)
	{
		this.role = role;
		this.category = category;
		this.leader = owner;
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
