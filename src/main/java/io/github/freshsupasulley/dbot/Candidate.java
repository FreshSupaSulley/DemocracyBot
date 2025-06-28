package io.github.freshsupasulley.dbot;

import net.dv8tion.jda.api.entities.Role;

public class Candidate {
	
	private int slot;
	private long id;
	private String slogan;
	private long roleID;
	
	public Candidate(int slot, long id, String slogan, Role role)
	{
		this.slot = slot;
		this.id = id;
		this.slogan = slogan;
		this.roleID = role.getIdLong();
	}
	
	public int getSlot()
	{
		return slot;
	}
	
	public long getID()
	{
		return id;
	}
	
	public String getSlogan()
	{
		return slogan;
	}
	
	public long getRoleID()
	{
		return roleID;
	}
	
	public void setSlogan(String slogan)
	{
		this.slogan = slogan;
	}
}
