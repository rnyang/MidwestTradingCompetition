package org.chicago.cases.arb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapBasedLatencyManager implements ILatencyQueue {
	
	Map<Integer, List<QueueEvent>> eventMap = new HashMap<Integer, List<QueueEvent>>();

	@Override
	public void addQueueEvent(QueueEvent event) {
		int tick = event.deliveryTick;
		List<QueueEvent> events = eventMap.get(tick);
		if (events == null) {
			events = new ArrayList<QueueEvent>();
			eventMap.put(tick, events);
		}
		events.add(event);
	}

	@Override
	public List<QueueEvent> getAllEventsForTick(int tick) {
		return eventMap.get(tick);
	}

}
