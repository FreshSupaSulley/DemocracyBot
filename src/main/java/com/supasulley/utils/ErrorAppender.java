package com.supasulley.utils;

import java.util.function.BiConsumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Allows a consumer to receive error log messages.
 */
public class ErrorAppender extends AppenderBase<ILoggingEvent> {
	
	private static final int CONSECUTIVE_INTERVAL = 1000;
	private static final int DECREASE_RATE = CONSECUTIVE_INTERVAL * 100;
	
	private static BiConsumer<Integer, ILoggingEvent> callback;
	private static long lastError = System.currentTimeMillis();
	private static int consecutiveErrors;
	
	public static void setErrorCallback(BiConsumer<Integer, ILoggingEvent> callback)
	{
		ErrorAppender.callback = callback;
	}
	
	@Override
	protected void append(ILoggingEvent event)
	{
		if(event.getLevel().toInt() >= Level.ERROR_INT)
		{
			// Forward error to callback
			// Get time between errors
			long distance = System.currentTimeMillis() - lastError;
			
			// If this error occurred too soon after the last
			if(distance < CONSECUTIVE_INTERVAL)
			{
				consecutiveErrors++;
			}
			// If we haven't had an error in a while
			else
			{
				// 100 seconds needs to pass to decrease consecutive errors by 1
				consecutiveErrors = Math.max(0, consecutiveErrors - (int) (distance / DECREASE_RATE));
			}
			
			lastError = System.currentTimeMillis();
			
			// Forward to callback
			callback.accept(consecutiveErrors, event);
		}
	}
}
