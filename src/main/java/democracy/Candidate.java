package democracy;

import net.dv8tion.jda.api.entities.Role;

public class Candidate {
	
	private long id;
	private String slogan;
	private Role role;
	
	public Candidate(long id, String slogan, Role role)
	{
		this.id = id;
		this.slogan = slogan;
		this.role = role;
	}
	
	public long getID()
	{
		return id;
	}
	
	public String getSlogan()
	{
		return slogan;
	}
	
	public Role getRole()
	{
		return role;
	}
}
