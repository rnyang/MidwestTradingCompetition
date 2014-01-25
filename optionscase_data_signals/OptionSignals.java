package org.chicago.cases.options;

import com.optionscity.freeway.api.messages.Signal;

public class OptionSignals {
	
	public static enum MsgType {
		VEGA,
		GAMMA,
		DELTA,
		VOL
	}
	
	public static class VolMessage extends Signal {
		
		public final MsgType type;
		public final int magnitude;
		
		public VolMessage(MsgType type, int magnitude) {
			super(VolMessage.class.getSimpleName());
			this.type = type;
			this.magnitude = magnitude;
		}
		
	}
	
	public static class AdminMessage extends Signal {
		
		public final MsgType type;
		public final int magnitude;
		
		public AdminMessage(MsgType type, int magnitude) {
			super(AdminMessage.class.getSimpleName());
			this.type = type;
			this.magnitude = magnitude;
		}
		
	}
	
	public static class ForecastMessage extends Signal {
		
		public final MsgType type;
		public final int magnitude;
		
		public ForecastMessage(MsgType type, int magnitude) {
			super(ForecastMessage.class.getSimpleName());
			this.type = type;
			this.magnitude = magnitude;
		}
		
	}

}
