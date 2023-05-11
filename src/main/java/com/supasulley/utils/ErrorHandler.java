package com.supasulley.utils;

import java.io.FileNotFoundException;

import democracy.DMain;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;

public class ErrorHandler {
	
	static
	{
		if(!DMain.inIDE)
		{
			DMain.log("Using custom streams");
			
			// Set console output to Main logger
			// Anything getting printed to the console is bad
			try {
				System.setOut(new CustomStream(false));
				System.setErr(new CustomStream(true));
			} catch(FileNotFoundException e) {
				DMain.log.error("Could not set out / err streams!");
				DMain.log(e);
				System.exit(1);
			}
		}
	}
	
	private PrivateChannel errorChannel;
	
	private boolean stopSendingUpdates;
	private int errorMessages;
	private long lastError;
	
	public ErrorHandler(PrivateChannel errorChannel)
	{
		this.errorChannel = errorChannel;
	}
	
	/**
	 * Queue error messages to send to operator
	 */
	public void queue(String string)
	{
		// PLEASE I GOD WANNA GET RID OF THIS
		if(string.contains("WARN net.dv8tion.jda.internal.requests.RateLimiter - Encountered 429") || string.contains("INFO"))
		{
			DMain.log("Refusing to queue (not an error) - " + string);
			return;
		}
		
		// Reset if this message isn't being spammed
		if(System.currentTimeMillis() - lastError > 5000)
		{
			errorMessages = 0;
		}
		
		errorMessages++;
		
		if(!stopSendingUpdates)
		{
			// We need to shutdown because a fatal error is occurring
			if(errorMessages >= 10)
			{
				stopSendingUpdates = true;
				send("Something is fatally wrong with " + DMain.BOT_NAME + ". Check logs immediately.");
			}
			
			if(string.length() < 2000)
			{
				send(string + " - Quick errors: " + errorMessages);
			}
			else
			{
				send("A very large error occured! Check logs! - Quick errors: " + errorMessages);
			}
		}
		
		lastError = System.currentTimeMillis();
	}
	
	private void send(String message)
	{
		errorChannel.sendMessage(message).queue();
	}
}
