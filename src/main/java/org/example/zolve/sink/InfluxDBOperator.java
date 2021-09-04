package org.example.zolve.sink;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBPoint;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.HashMap;

public class InfluxDBOperator extends ProcessFunction<String, InfluxDBPoint> {
	OutputTag<String> outputTag = new OutputTag<String>("side-output") {};

	@Override
	public void processElement(String s, Context context, Collector<InfluxDBPoint> collector) throws Exception {
		JSONParser parser = new JSONParser();
		JSONObject parsed = (JSONObject) parser.parse(s);
		String measurement = (String) parsed.get("measurement");
		long timestamp = System.currentTimeMillis();

		HashMap<String, String> tags = new HashMap<>();
		tags.put("status", "STATUS");
		tags.put("userId", (String) parsed.get("userId"));

		HashMap<String, Object> fields = new HashMap<>();
		fields.put("userId", parsed.get("userId"));
		fields.put("transactionId", parsed.get("transactionId"));
		fields.put("status", parsed.get("status"));
		collector.collect(new InfluxDBPoint(measurement, timestamp, tags, fields));
//		collector.collect(s);


	}
}
