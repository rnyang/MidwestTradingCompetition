package org.chicago.cases.arb;

import org.chicago.cases.AbstractExchangeArbCase.CustomerSide;
import org.chicago.cases.AbstractExchangeArbCase.Exchange;
import org.chicago.cases.arb.ArbSignals.CustomerOrder;
import org.chicago.cases.arb.ArbSignals.TopOfBookUpdate;
import org.chicago.cases.arb.ArbSignals.CustomerOrder;
import org.chicago.cases.AbstractExchangeArbCase.CustomerSide;


import com.optionscity.freeway.api.messages.Signal;
import com.optionscity.freeway.api.services.IPlaybackService.ISignalProcessor;
import org.chicago.cases.utils.TeamUtilities;

public class ArbSignalProcessor implements ISignalProcessor {

	/*
	 * We'll only be importing signals, so the serialization method is not needed.
	 */
	public String asString(Signal signalString) {return "";}

	@Override
	public Signal fromString(String signalString) {
		String[] parts = signalString.split(";");
		if (parts.length < 1)
			throw new IllegalStateException("Invalid number of fields in signal String");

		if (parts[0].equals("ORDER")){
			// 0 = Customer Buy, 1 = Customer Sell
			Exchange exchange;
			if(parts[1].equals("ROBOT")){
				exchange = Exchange.ROBOT;
			}else if(parts[1].equals("SNOW")){
				exchange = Exchange.SNOW;
			}else{
				throw new IllegalStateException("Unknown exchange in Customer Order");
			}

			CustomerSide side = CustomerSide.values()[Integer.parseInt(parts[2])];
			double price = Double.parseDouble(parts[3]);

			//log("SYSTEM: Processed customer order of " + side + " at " + price + " from " + exchange);

			return new CustomerOrder(exchange,side,price);
		}
        else if (parts[0].equals("BOOKUPDATE")){

            Quote robotQuote;
            Quote snowQuote;

            double robotBid = Double.parseDouble(parts[2]);
            double robotAsk = Double.parseDouble(parts[3]);
            robotQuote = new Quote(Exchange.ROBOT, robotBid, robotAsk);

            double snowBid = Double.parseDouble(parts[5]);
            double snowAsk = Double.parseDouble(parts[6]);
            snowQuote = new Quote(Exchange.SNOW, snowBid, snowAsk);
            /*
            if (parts[1].equals("ROBOT")){
			    double robotBid = Double.parseDouble(parts[2]);
			    double robotAsk = Double.parseDouble(parts[3]);
			    robotQuote = new Quote(Exchange.ROBOT, robotBid, robotAsk);
            }
            else{
                throw new IllegalStateException("No ROBOT exchange orders.");
            }

            if (parts[4].equals("SNOW")){
			    double snowBid = Double.parseDouble(parts[5]);
			    double snowAsk = Double.parseDouble(parts[6]);
			    snowQuote = new Quote(Exchange.SNOW, snowBid, snowAsk);
            }
            else{
                throw new IllegalStateException("No SNOW exchange orders.");
            }
*/
			//log("SYSTEM: Processed bookupdate (ROBOT,"+robotQuote.bidPrice+","+robotQuote.askPrice+") and (SNOW,"+snowQuote.bidPrice+","+snowQuote.askPrice+")");
			return new TopOfBookUpdate(snowQuote, robotQuote);
		}
		return null;
	}

}
