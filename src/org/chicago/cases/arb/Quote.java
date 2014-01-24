package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.Exchange;

// Simple data encapsulation object
public class Quote {
	
	// Immutable state, so having these public is fine
	public final Exchange exchange;
	public final double bidPrice;
	public final double askPrice;
	
	public Quote(Exchange exchange, double bidPrice, double askPrice) {
		this.exchange = exchange;
		this.bidPrice = bidPrice;
		this.askPrice = askPrice;
	}

}
