package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.Exchange;
import org.chicago.cases.AbstractExchangeArbCase.AlgoSide;

import org.chicago.cases.arb.Quote;



public abstract class QueueEvent {

	public final int tick;
	
	protected QueueEvent(int tick) {
		this.tick = tick;
	}
	
	public static class DelayedOrderFill extends QueueEvent {
		
		public final AlgoSide algoside;
		public final double price;
		public final Exchange exchange;
		
		
		public DelayedOrderFill(int tick, AlgoSide algoside, double price, Exchange exchange) {
			super(tick);
			this.algoside = algoside;
			this.price = price;
			this.exchange = exchange;	
		}
		
	}

	public static class DelayedTopOfBook extends QueueEvent {

		public final Quote[] quotes;

		public DelayedTopOfBook(int tick, Quote[] quotes) {
			super(tick);
			this.quotes = quotes;
		}
	}
	
}
