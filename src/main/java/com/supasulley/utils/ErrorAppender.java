package com.supasulley.utils;

import java.util.function.Consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class ErrorAppender extends AppenderBase<ILoggingEvent> {
	
	private static Consumer<ILoggingEvent> callback;
	
	public static void setErrorCallback(Consumer<ILoggingEvent> callback)
	{
		ErrorAppender.callback = callback;
	}
	
	@Override
	protected void append(ILoggingEvent event)
	{
		if(event.getLevel().toInt() >= Level.ERROR_INT)
		{
			// Forward error to callback
			callback.accept(event);
		}
	}
}
