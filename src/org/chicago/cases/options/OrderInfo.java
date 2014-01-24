package org.chicago.cases.options;

// Simple data encapsulation object
public class OrderInfo {
	
	public static enum OrderSide {
		BUY, SELL;
	}
	
	// Immutable state, so having these public is fine
	public final String idSymbol;
	public final OrderSide side;
	public final double price;
	public final int quantity;
	
	public OrderInfo(String idSymbol, OrderSide side, double price, int quantity) {
		this.idSymbol = idSymbol;
		this.side = side;
		this.price = price;
		this.quantity = quantity;
	}

}
