package io.github.freshsupasulley.dbot;

// damn records aren't a thing in this java version :(
// getters and setters it is
public class PoliticalParty {
	
	private final long owner, role, category;
	
	public PoliticalParty(long role, long category, long owner)
	{
		this.role = role;
		this.category = category;
		this.owner = owner;
	}
	
	public long getRole()
	{
		return role;
	}
	
	public long getCategory()
	{
		return category;
	}
	
	public long getOwnerID()
	{
		return owner;
	}
	
	public boolean equals(PoliticalParty party)
	{
		return party.role == this.role;
	}
}
