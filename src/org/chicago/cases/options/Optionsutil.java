package org.chicago.cases.options;

import org.apache.commons.math3.distribution.NormalDistribution;

public class Optionsutil {

	public static double Call(double S, double K,double t,double r,double vol){
		double d_1 = (Math.log(1.0*S/K)+(r+Math.pow(vol, 2)/2)*t)/(vol*Math.sqrt(t));
		double d_2 = (Math.log(1.0*S/K)+(r-Math.pow(vol, 2)/2)*t)/(vol*Math.sqrt(t));
		NormalDistribution norm = new NormalDistribution();
		double rv = norm.cumulativeProbability(d_1)*S - norm.cumulativeProbability(d_2)*K*Math.exp(-r*t);
		return rv;
	}
	
	public static double calculateDelta(double spot, double strike,double time,double rate,double vol){
		double d_1 = (Math.log(1.0*spot/strike)+(rate+Math.pow(vol, 2)/2)*time)/(vol*Math.sqrt(time));
		NormalDistribution norm = new NormalDistribution();
		return norm.cumulativeProbability(d_1);
	}
	
	public static double calculateGamma(double S, double K,double t,double r,double vol){
		double d_1 = (Math.log(1.0*S/K)+(r+Math.pow(vol, 2)/2)*t)/(vol*Math.sqrt(t));
		NormalDistribution norm = new NormalDistribution();
		return norm.density(d_1)/(S*vol*Math.sqrt(t));
	}
	
	public static double calculateVega(double S, double K,double t,double r,double vol){
		double d_1 = (Math.log(1.0*S/K)+(r+Math.pow(vol, 2)/2)*t)/(vol*Math.sqrt(t));
		NormalDistribution norm = new NormalDistribution();
		return norm.density(d_1)*S*Math.sqrt(t);
	}
}


