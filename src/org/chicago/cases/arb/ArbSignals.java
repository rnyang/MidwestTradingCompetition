package org.chicago.cases.arb;

import com.optionscity.freeway.api.messages.Signal;

public class ArbSignals {
	
	
	public static class TopOfBookUpdate extends Signal {
		
		public final Quote snowQuote;
		public final Quote robotQuote;
		
		public TopOfBookUpdate(Quote snowQuote, Quote robotQuote) {
			super(TopOfBookUpdate.class.getSimpleName());
			this.snowQuote = snowQuote;
			this.robotQuote = robotQuote;
		}
		
	}

}
