package org.chicago.cases;

import com.optionscity.freeway.api.messages.Signal;

public class CommonSignals {
	
	public static class EndSignal extends Signal {	
		public EndSignal() {
			super(EndSignal.class.getSimpleName());
		}	
	}

}
