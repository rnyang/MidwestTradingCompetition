package org.chicago.cases.options;

import com.optionscity.freeway.api.messages.Signal;

public class OptionSignals {
	
	
	public static class VolUpdate extends Signal {
		public final double impliedVol;
		
		public VolUpdate(double impliedVol) {
			super(VolUpdate.class.getSimpleName());
			this.impliedVol = impliedVol;
		}
	}
	
	public static class ForecastMessage extends Signal {
		
		public final double delta;
		public final double gamma;
		public final double vega;
		
		public ForecastMessage(double delta, double gamma, double vega) {
			super(ForecastMessage.class.getSimpleName());
			this.delta = delta;
			this.gamma = gamma;
			this.vega = vega;
		}
		
	}
	
	public static class AdminMessage extends Signal {
		
		public final double minDelta;
		public final double minGamma;
		public final double minVega;
		public final double maxDelta;
		public final double maxGamma;
		public final double maxVega;
		
		public AdminMessage(double minDelta, double maxDelta, double minGamma, double maxGamma, double minVega, double maxVega) {
			super(AdminMessage.class.getSimpleName());
			this.minDelta = minDelta;
			this.minGamma = minGamma;
			this.minVega = minVega;
			this.maxDelta = maxDelta;
			this.maxGamma = maxGamma;
			this.maxVega = maxVega;
		}
		
	}

}
