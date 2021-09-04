package org.example.zolve.keySelector;

import org.apache.flink.api.java.functions.KeySelector;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class KeyByUserIdTimeOperator implements KeySelector<String, String> {
	// This class emits key combination of userId and timestamp


	@Override
	public String getKey(String s) throws Exception {
		StringBuilder keyString = new StringBuilder();
		JSONParser parser = new JSONParser();
		JSONObject parsed = (JSONObject) parser.parse(s);
		keyString.append((String) parsed.get("userId"));
		keyString.append(parsed.get("timestamp").toString());
		return keyString.toString();
	}
}
