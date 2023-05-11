package com.supasulley.utils;

import java.io.FileNotFoundException;
import java.io.PrintStream;

import democracy.DMain;

public class CustomStream extends PrintStream {
	
	private boolean isErrorStream;
	
	public CustomStream(boolean isErrorStream) throws FileNotFoundException
	{
		super(DMain.logs);
		
		this.isErrorStream = isErrorStream;
	}
	
	@Override
	public void println(String string)
	{
		if(!isErrorStream)
		{
			DMain.log(string);
		}
		else
		{
			DMain.error(string);
		}
	}
}
