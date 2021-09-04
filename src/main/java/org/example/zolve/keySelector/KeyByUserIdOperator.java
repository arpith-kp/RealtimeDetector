package org.example.zolve.keySelector;

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class KeyByUserIdOperator implements KeySelector<String, String> {
	// This class filters message to fetch key userId


	@Override
	public String getKey(String s) throws Exception {
		JSONParser parser = new JSONParser();
		JSONObject parsed = (JSONObject) parser.parse(s);
		return (String) parsed.get("userId");
	}
}
