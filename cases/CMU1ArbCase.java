import org.chicago.cases.AbstractExchangeArbCase;
import org.chicago.cases.arb.Quote;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class CMU1ArbCase extends AbstractExchangeArbCase {

    class MySampleArbImplementation implements ArbCase {


        // Note...the IDB will be used to save data to the hard drive and access it later
        // This will be useful for retrieving data between rounds
        private IDB myDatabase;
        private double delta;
        int position;
        double[] desiredRobotPrices = new double[2];
        double[] desiredSnowPrices = new double[2];


        public void addVariables(IJobSetup setup) {
            // Registers a variable with the system.

            //We are changin magic number.
            setup.addVariable("delta", "magic number", "double", "0.18");
        }

        public void initializeAlgo(IDB database) {
            // Databases can be used to store data between rounds
            myDatabase = database;
            this.delta = getDoubleVar("delta");
        }

        @Override
            public void fillNotice(Exchange exchange, double price, AlgoSide algoside) {
                log("My quote was filled with at a price of " + price + " on " + exchange + " as a " + algoside);
                if(algoside == AlgoSide.ALGOBUY){
                    position += 1;
                }else{
                    position -= 1;
                }
            }

        @Override
            public void positionPenalty(int clearedQuantity, double price) {
                log("I received a position penalty with " + clearedQuantity + " positions cleared at " + price);
                position -= clearedQuantity;
            }

        @Override
            public void newTopOfBook(Quote[] quotes) {
                for (Quote quote : quotes) {
                    log("I received a new bid of " + quote.bidPrice + ", and ask of " + quote.askPrice + " from " + quote.exchange);
                }

                double midRobot=(quotes[0].bidPrice+quotes[0].askPrice)/2;
                double midSnow=(quotes[1].bidPrice+quotes[1].askPrice)/2;


                desiredRobotPrices[0] = midRobot - delta;
                desiredRobotPrices[1] = midRobot + delta;


                desiredSnowPrices[0] = midSnow - delta;
                desiredSnowPrices[1] = midSnow + delta;
            }

        @Override
            public Quote[] refreshQuotes() {
                Quote[] quotes = new Quote[2];
                quotes[0] = new Quote(Exchange.ROBOT, desiredRobotPrices[0], desiredRobotPrices[1]);
                quotes[1] = new Quote(Exchange.SNOW, desiredSnowPrices[0], desiredSnowPrices[1]);
                return quotes;
            }

    }

    @Override
        public ArbCase getArbCaseImplementation() {
            return new MySampleArbImplementation();
        }

}
