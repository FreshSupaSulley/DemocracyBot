package io.github.freshsupasulley.dbot.polls;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.github.freshsupasulley.dbot.Main;

public class PollAdapter implements JsonDeserializer<Poll>, JsonSerializer<Poll> {
	
	@Override
	public Poll deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
	{
		JsonObject data = json.getAsJsonObject();
		
		try {
			return context.deserialize(data, Class.forName(data.get("type").getAsString()));
		} catch(ClassNotFoundException e) {
			Main.log.error("Failed to deserialize poll {}", json, e);
			return null;
		}
	}
	
	@Override
	public JsonElement serialize(Poll src, Type typeOfSrc, JsonSerializationContext context)
	{
		JsonObject element = context.serialize(src).getAsJsonObject();
		element.addProperty("type", src.getClass().getName());
		return element;
	}
}