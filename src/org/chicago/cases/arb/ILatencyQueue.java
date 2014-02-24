package org.chicago.cases.arb;

import java.util.List;

import com.optionscity.freeway.api.messages.Signal;

public interface ILatencyQueue {
	
	public void addQueueEvent(QueueEvent event);
	
	public List<QueueEvent> getAllEventsForTick(int tick);
	

}
