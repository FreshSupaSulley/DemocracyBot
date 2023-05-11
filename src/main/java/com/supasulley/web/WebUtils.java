package com.supasulley.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import democracy.DMain;

/**
 * Creates HTTP requests for interacting with third-parties.
 */
public class WebUtils {
	
	// For parsing JSON
	public static final TypeReference<Map<String, Object>> MAP = new TypeReference<Map<String, Object>>() {};
	public static final TypeReference<List<Map<String, Object>>> MAP_ARRAYS = new TypeReference<List<Map<String, Object>>>() {};
	public static final TypeReference<List<String>> UNMAPPED_ARRAY = new TypeReference<List<String>>() {};
	public static final TypeReference<List<List<String>>> UNMAPPED_ARRAYS = new TypeReference<List<List<String>>>() {};
	public static final TypeReference<List<List<List<String>>>> NESTED_UNMAPPED_ARRAYS = new TypeReference<List<List<List<String>>>>() {};
	
	private static final ObjectMapper mapper = new ObjectMapper();
	
	// Web-standard text encoding
	private static final Charset HTTP_ENCODING = StandardCharsets.UTF_8;
	private static final DecimalFormat format = new DecimalFormat("#,###");
	
	/**
	 * Makes an HTTP GET request to the provided URI.
	 * @param uri The URI to make the request to
	 * @param headers Optional additional headers
	 * @return Response from provided URI
	 * @throws IOException an error occurred making the request or if server returned an error code
	 */
	public static String getRequest(String uri, @Nullable NameValuePair... headers) throws IOException
	{
		HttpGet get = null;
		
		// Merge URISyntaxException into IOException
		try {
			get = new HttpGet(new URI(uri));
		} catch(URISyntaxException e) {
			DMain.log(e);
			throw new IOException(e);
		}
		
		get.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
		
		for(NameValuePair pair : headers)
		{
			get.setHeader(pair.getName(), pair.getValue());
		}
		
		return buildRequest(get);
	}
	
	/**
	 * Makes an HTTP POST request to the provided URI.
	 * @param uri The URI to make the request to
	 * @param entity The HTTPEntity to attach to the request, such as a body with StringEntity.
	 * @param headers Optional additional headers
	 * @return Response from provided URI
	 * @throws IOException an error occurred making the request or if server returned an error code
	 */
	public static String postRequest(String uri, @Nullable HttpEntity entity, @Nullable NameValuePair... headers) throws IOException
	{
		HttpPost post = null;
		
		// Merge URISyntaxException into IOException
		try {
			post = new HttpPost(new URI(uri));
		} catch(URISyntaxException e) {
			DMain.log(e);
			throw new IOException(e);
		}
		
		post.setEntity(entity);
		post.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
		
		for(NameValuePair pair : headers)
		{
			post.setHeader(pair.getName(), pair.getValue());
		}
		
		return buildRequest(post);
	}
	
	public static String postRequest(String uri, @Nullable NameValuePair... headers) throws IOException
	{
		return WebUtils.postRequest(uri, null, headers);
	}
	
	private static String buildRequest(HttpRequestBase request) throws IOException
	{
		// Execute request and read response
		CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
		HttpResponse response = client.execute(request);
		
		// Throw error for unexpected result
		StatusLine status = response.getStatusLine();
		int code = status.getStatusCode();
		
		// Get response
		StringBuilder result = new StringBuilder();
		InputStream stream = response.getEntity().getContent();
		
		for(int read = 0; (read = stream.read()) != -1;)
		{
			result.append((char) read);
		}
		
		if(code < 200 || code > 299)
		{
			throw new ErrorCodeException(status, result.toString());
		}
		
		return result.toString();
	}
	
	/**
	 * Transforms raw JSON into a string.
	 * @param raw raw JSON
	 * @return escaped string
	 */
	public static String escape(String raw)
	{
		return TextNode.valueOf(raw).toString();
	}
	
	/**
	 * Formats the provided time into GMT time with the specified pattern. This method uses the universal time zone (UTC/GMT).
	 * @param pattern The pattern describing the date and time format
	 * @param time The time in milliseconds
	 * @return Formatted time
	 */
	public static String formatTime(String pattern, long time)
	{
		SimpleDateFormat format = new SimpleDateFormat(pattern);
		format.setTimeZone(TimeZone.getTimeZone("GMT"));
		return format.format(new Date(time));
	}
	
	/**
	 * Encodes text into the HTML standard ISO 8859-1 charset.
	 * @param text the text to encode
	 * @return text
	 */
	public static String urlEncode(String text)
	{
		return URLEncoder.encode(text, HTTP_ENCODING);
	}
	
	/**
	 * Decodes text from the HTML standard ISO 8859-1 charset.
	 * @param text the text to decode
	 * @return encoded text
	 */
	public static String urlDecode(String text)
	{
		return URLDecoder.decode(text, HTTP_ENCODING);
	}
	
	/**
	 * Unescaping HTML makes HTML codes (like &#39;) into readable values (like ').
	 * @param raw raw HTML
	 * @return unescaped string
	 */
	public static String unescapeHTML(String raw)
	{
		return StringEscapeUtils.unescapeHtml4(raw);
	}
	
	/**
	 * Duplicates the number of backslashes (Discord needs 2 to display them)
	 * and places a backslash before all underscores to avoid markdown.
	 * @param raw raw text
	 * @return unescaped string
	 */
	public static final String unescapeMarkdown(String raw)
	{
		return raw.replace("\\", "\\\\").replace("_", "\\_");
	}
	
	/**
	 * Parses JSON data into a provided format.<br><br>
	 * Used when JSON is too deeply nested to resolve with simple methods.
	 * @param <T> returned object
	 * @param raw raw JSON
	 * @param type determines how to deserialize the data
	 * @return new object (defined by TypeReference)
	 */
	public static <T> T parseJSON(TypeReference<T> type, String raw)
	{
		try {
			return mapper.readValue(raw, type);
		} catch(JsonProcessingException e) {
			DMain.error("An error occured parsing JSON from raw: " + raw);
			DMain.log(e);
			return null;
		}
	}
	
	/**
	 * Creates an ArrayNode object to build JSON arrays.
	 * @return new ArrayNode
	 */
	public static ArrayNode createArrayNode()
	{
		return mapper.createArrayNode();
	}
	
	/**
	 * Creates an ObjectNode object to build JSON objects.
	 * @return new ArrayNode
	 */
	public static ObjectNode createObjectNode()
	{
		return mapper.createObjectNode();
	}
	
	/**
	 * Adds the appropriate commas within larger numbers.
	 * @param number number to format
	 * @return formatted number as a string
	 */
	public static String addCommas(long number)
	{
		return format.format(number);
	}
	
	/**
	 * Utility method for casting raw JSON value into a long.
	 * @param value JSON value to cast
	 * @return value of long
	 */
	public static long castLong(Object value)
	{
		if(!(value instanceof Number))
		{
			DMain.error("Tried to cast " + value.getClass() + " into a number. Raw: " + value);
			return 0;
		}
		
		return ((Number) value).longValue();
	}
}
