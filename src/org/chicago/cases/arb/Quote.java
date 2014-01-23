package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.Exchange;

// Simple data encapsulation object
public class Quote {
	
	// Immutable state, so having these public is fine
	public final Exchange exchange;
	public final double bidPrice;
	public final int bidQuantity;
	public final double askPrice;
	public final int askQuantity;
	
	public Quote(Exchange exchange, double bidPrice, int bidQuantity, double askPrice, int askQuantity) {
		this.exchange = exchange;
		this.bidPrice = bidPrice;
		this.bidQuantity = bidQuantity;
		this.askPrice = askPrice;
		this.askQuantity = askQuantity;
	}

}
