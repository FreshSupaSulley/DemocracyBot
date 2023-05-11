package com.supasulley.web;

import org.apache.http.StatusLine;

import net.dv8tion.jda.api.exceptions.HttpException;

/**
 * Thrown when an HTTP server responds with a bad HTTP code.
 */
@SuppressWarnings("serial")
public class ErrorCodeException extends HttpException {
	
	private int code;
	private String entity;
	
	public ErrorCodeException(StatusLine status, String entity)
	{
		super(status.getProtocolVersion() + " code " + status.getStatusCode() + " - " + status.getReasonPhrase());
		
		this.code = status.getStatusCode();
		this.entity = entity;
	}
	
	public int getCode()
	{
		return code;
	}
	
	public String getEntity()
	{
		return entity;
	}
}
