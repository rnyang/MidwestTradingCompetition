package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.Exchange;
import org.chicago.cases.AbstractExchangeArbCase.AlgoSide;

import org.chicago.cases.arb.Quote;



public abstract class QueueEvent {

	public final int deliveryTick;
	
	protected QueueEvent(int deliveryTick) {
		this.deliveryTick = deliveryTick;
	}
	
	public static class DelayedOrderFill extends QueueEvent {
		
		public final AlgoSide algoside;
		public final double price;
		public final Exchange exchange;
		
		
		public DelayedOrderFill(int deliveryTick, AlgoSide algoside, double price, Exchange exchange) {
			super(deliveryTick);
			this.algoside = algoside;
			this.price = price;
			this.exchange = exchange;	
		}
		
	}

	public static class DelayedTopOfBook extends QueueEvent {

		public final Quote[] quotes;

		public DelayedTopOfBook(int deliveryTick, Quote[] quotes) {
			super(deliveryTick);
			this.quotes = quotes;
		}
	}
	
}
