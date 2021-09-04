package org.example.zolve.sink;


import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;

public class CollectSinkOperator<T> extends PrintSinkFunction<T> {
	static java.lang.String listString = "Sink :";

	StringBuilder ms = new StringBuilder();
	public CollectSinkOperator(){}

	@Override
	public synchronized void invoke(T value) {
		Object ms = value;
		super.invoke((T) (listString + ms.toString()));
	}
}
