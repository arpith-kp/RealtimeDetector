package org.example.zolve.flinkOperators;

import com.google.gson.JsonObject;
import com.squareup.moshi.Json;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

public class KeyedCoProcessOperator extends KeyedCoProcessFunction<String, String, String, String> {

	private transient MapState<String, String> tracker;
	private transient MapState<String, String> externalTracker;
	private MapStateDescriptor<String, String> trackerMapStateDesc;
	private MapStateDescriptor<String, String> externalMapStateDesc;
	static Logger log = Logger.getLogger(KeyedCoProcessOperator.class.getName());
	private JSONParser parser ;
	@Override
	public  void open(Configuration config) throws Exception {
		this.trackerMapStateDesc = new MapStateDescriptor<String, String>("fieldMapsInt", BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO);
		this.externalMapStateDesc = new MapStateDescriptor<String, String>("fieldMapsExt", BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO);
		this.tracker = getRuntimeContext().getMapState(trackerMapStateDesc);
		this.externalTracker = getRuntimeContext().getMapState(externalMapStateDesc);
		this.parser =  new JSONParser();
	}

	@Override
	public void processElement1(String s, Context context, Collector<String> collector) throws Exception {
		String userIdKey = context.getCurrentKey();
		this.tracker.put(userIdKey, s);
	}

	@Override
	public void processElement2(String externalRecord, Context ctx, Collector<String> collector) throws Exception {

		this.externalTracker.put(ctx.getCurrentKey(), externalRecord);
		long times = ctx.timerService().currentProcessingTime();
		ctx.timerService().registerEventTimeTimer(times+ 60000L);
	}

	@Override
	public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> collector) throws Exception {
		String externalRecord = this.externalTracker.get(ctx.getCurrentKey());
		JSONObject external_obj = (JSONObject) this.parser.parse(externalRecord);
		String internal_record = null;
		try {
			internal_record = this.tracker.get((String) external_obj.get("userId"));
		} catch (Exception e){
			log.info(" Matching record not found ");
		}

		if (internal_record != null){
			JSONObject internal_obj  = (JSONObject) this.parser.parse(internal_record);
			if (internal_obj.get("status") != external_obj.get("status")){
				JSONObject out_obj = new JSONObject();
				out_obj.put("userId",external_obj.get("userId"));
				out_obj.put("transactionId",external_obj.get("transactionId"));
				out_obj.put("status",external_obj.get("status"));
				out_obj.put("measurement","status_metrics");
				collector.collect(out_obj.toJSONString());
			}
		}

	}

}
