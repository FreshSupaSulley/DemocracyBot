package io.github.freshsupasulley.dbot;

public class Candidate extends ServerMember {
	
	private int slot;
	private String slogan;
	
	public Candidate(ServerMember parent, int slot, String slogan)
	{
		super(parent.getID());
		
		this.slot = slot;
		this.slogan = slogan;
	}
	
	public int getSlot()
	{
		return slot;
	}
	
	public String getSlogan()
	{
		return slogan;
	}
	
	public void setSlogan(String slogan)
	{
		this.slogan = slogan;
	}
}
