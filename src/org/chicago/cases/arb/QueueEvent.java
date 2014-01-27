package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.Exchange;
import org.chicago.cases.AbstractExchangeArbCase.AlgoSide;

import org.chicago.cases.arb.Quote;



public class QueueEvent {

	public final int tick;
	
	public static class OrderFill{
		
		public final AlgoSide algoside;
		public final double price;
		public final Exchange exchange;
		
		public OrderFill(int tick, AlgoSide algoside, double price, Exchange exchange) {
			this.tick = tick;
			this.algoside = algoside;
			this.price = price;
			this.exchange = exchange;	
		}
		
	}

	public static class TOBUpdate{

		public final Quote[] quotes;

		public TOBUpdate(int tick, Quote[] quotes) {
			this.tick = tick;
			this.quotes = quotes;
		}
	}
}
