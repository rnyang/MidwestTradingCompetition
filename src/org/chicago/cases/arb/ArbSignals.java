package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.CustomerSide;

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

	public static class CustomerOrder extends Signal {

		public final CustomerSide side;
		public final double price;

		public CustomerOrder(CustomerSide side, double price) {
			super(CustomerOrder.class.getSimpleName());
			this.side = side;
			this.price = price;
		}
	}
}
