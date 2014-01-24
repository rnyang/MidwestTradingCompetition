package org.chicago.cases.utils;

public class PriceGenerator {
	
	private final double start;
	private final double low;
	private final double high;
	private final double spread;
	private final double increment;
	private double direction;
	private double currentPrice;

	public PriceGenerator(double start, double low, double high, double spread, double increment) {
		this.start = start;
		this.low = low;
		this.high = high;
		this.spread = spread;
		this.increment = increment;
		direction = increment;
		currentPrice = start;
	}
	
	public double[] getNextPrices() {
		
		if (currentPrice >= high)
			direction = -increment;
		else if (currentPrice <= low)
			direction = increment;
		
		// Write out market data entry to file - assume quantity of 10000
		double currentBid = currentPrice - (spread * increment);
		double currentAsk = currentPrice + (spread * increment);
		
		currentPrice += direction;
		
		return new double[] { currentBid, currentAsk };
	}

}
