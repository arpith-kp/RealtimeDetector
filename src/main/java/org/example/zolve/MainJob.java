package org.example.zolve;

import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.io.TextInputFormat;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.FileProcessingMode;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBConfig;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBPoint;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBSink;
import org.example.zolve.flinkOperators.DedupOperator;
import org.example.zolve.flinkOperators.KeyedCoProcessOperator;
import org.example.zolve.keySelector.KeyByUserIdOperator;
import org.example.zolve.keySelector.KeyByUserIdTimeOperator;
import org.example.zolve.sink.CollectSinkOperator;
import org.example.zolve.sink.InfluxDBOperator;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class MainJob {
	public static void main(String[] args) throws Exception {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		env.setRestartStrategy(RestartStrategies.failureRateRestart(
				3,                         // max failures per interval
				Time.of(5, TimeUnit.MINUTES), //time interval for measuring failure rate
				Time.of(10, TimeUnit.SECONDS) // delay
		));
		env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
		env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);
		env.setStateBackend(new EmbeddedRocksDBStateBackend());
		env.getCheckpointConfig().setCheckpointStorage("file:///Users/z003fwy/workspace/delete/Zolve/checkpointdir");

		ParameterTool readArgs = ParameterTool.fromArgs(args);
		String propFilePath = readArgs.get("input");
		ParameterTool params = ParameterTool.fromPropertiesFile(propFilePath);

		String external_data_path = params.get("external_data_path");
		String internal_data_status_mismatch = params.get("internal_data_status_mismatch");
		long checkpointInterval = params.getLong("checkpointInterval", 1000);
		env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
		env.enableCheckpointing(checkpointInterval);

		// External Stream Data handler
		File externalBaseDir = new File(external_data_path).getAbsoluteFile();
		String externalFileUri = "file:///" + externalBaseDir + "/";
		TextInputFormat externalFileFormat = new TextInputFormat(new org.apache.flink.core.fs.Path(externalFileUri));
		DataStream<String> externalStream = env.readFile(externalFileFormat, externalFileUri,
				FileProcessingMode.PROCESS_ONCE, 1000).name("ExternalInputDataReader");

		KeyByUserIdTimeOperator keyByUserIdTimeOperator = new KeyByUserIdTimeOperator();
		DedupOperator dedupFilterOperator = new DedupOperator(keyByUserIdTimeOperator, 10 * 1000);
		KeyByUserIdOperator keyedByOperator = new KeyByUserIdOperator();
		KeyedCoProcessOperator coProcessOperator = new KeyedCoProcessOperator();
		InfluxDBOperator influxDBOperator = new InfluxDBOperator();
		DataStream<String> externalDedupedMsgStream = externalStream.keyBy(keyedByOperator).filter(dedupFilterOperator).name("Deduplicator");

		// Internal Stream Data Handler
		File internalBaseDir = new File(internal_data_status_mismatch).getAbsoluteFile();
		String internalFileUri = "file:///" + internalBaseDir + "/";
		TextInputFormat internalFileFormat = new TextInputFormat(new org.apache.flink.core.fs.Path(internalFileUri));
		DataStream<String> internalStream = env.readFile(internalFileFormat, internalFileUri,
				FileProcessingMode.PROCESS_ONCE, 1000).name("InternalInputDataReader");

		DataStream<String> outStream = internalStream.keyBy(keyedByOperator)
				.connect(externalDedupedMsgStream.keyBy(keyedByOperator))
				.process(coProcessOperator).name("UnionStreamProcessor").setParallelism(2);

		DataStream<InfluxDBPoint> influxStream = outStream.process(influxDBOperator).name("InfluxOutputter");

		InfluxDBConfig influxDBConfig = InfluxDBConfig.builder("http://localhost:8086", "admin", "admin", "zolve_metrics")
				.batchActions(1000)
				.flushDuration(100, TimeUnit.MILLISECONDS)
				.enableGzip(true)
				.build();

		influxStream.addSink(new InfluxDBSink(influxDBConfig)).name("InfluxDBSink");
		influxStream.addSink(new CollectSinkOperator<>()).name("PrintResults").setParallelism(2);
		env.execute("Streaming application");
	}
}
