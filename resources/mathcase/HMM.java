import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import com.google.common.base.*;
import com.google.common.collect.Iterables;


/**
 * Created with IntelliJ IDEA.
 * User: kangpingzhu
 * Date: 1/19/14
 * Time: 5:12 PM
 * To change this template use File | Settings | File Templates.
 */



public class HMM {

    double[][] data;
    int MaxLine = 300;


    double[][] stateTransProb = {{0.5,0.5},{0.5,0.5}};
    HashMap<Integer, Double>  firstStateEmisProb;
    HashMap<Integer, Double>  secondStateEmisProb;
    HashMap<Integer, Double>  firstStateSpreadProb;
    HashMap<Integer, Double>  secondStateSpreadProb;
    HashMap<Integer, Integer> changeMap;
    HashMap<Integer, Integer> spreadMap;

    int[] changeState = {0,1,-1,3,-3,5,-5,10,-10};

    int[] spreadState = {1,3,5};
    //double[] firstStateEmisProb = { 0.2, 0.15, 0.05,0.15, 0.05,0.15, 0.05,0.15, 0.05, 0.15, 0.05 };
    //double[] firstStateSpreadProb = { 0.33, 0.33, 0.34};
   // double[] secondStateEmisProb = { 0.2, 0.05, 0.15,0.05, 0.15,0.05, 0.15,0.05, 0.15, 0.05, 0.15 };
   // double[] secondStateSpreadProb = { 0.33, 0.33, 0.34};
    Integer[][] priceChangeAndSpread;

    double price = 9990;
    Integer change = 0;
    Integer spread = 5;
    int time = 0;
    int share = 0;
    double cash =0;
    double[] stateProb = { 0.5, 0.5 };

    public HMM(){
        firstStateEmisProb = new HashMap<Integer, Double>();
        firstStateEmisProb.put(0,0.05);
        firstStateEmisProb.put(1,0.15);
        firstStateEmisProb.put(-1,0.05);
        firstStateEmisProb.put(3,0.15);
        firstStateEmisProb.put(-3,0.05);
        firstStateEmisProb.put(5,0.15);
        firstStateEmisProb.put(-5,0.05);
        firstStateEmisProb.put(10,0.10);
        firstStateEmisProb.put(-10,0.05);

        secondStateEmisProb = new HashMap<Integer, Double>();
        secondStateEmisProb.put(0,0.05);
        secondStateEmisProb.put(-1,0.15);
        secondStateEmisProb.put(1,0.05);
        secondStateEmisProb.put(-3,0.15);
        secondStateEmisProb.put(3,0.05);
        secondStateEmisProb.put(-5,0.15);
        secondStateEmisProb.put(5,0.05);
        secondStateEmisProb.put(-10,0.10);
        secondStateEmisProb.put(10,0.05);

        changeMap = new HashMap<Integer, Integer>();
        changeMap.put(0,0);
        changeMap.put(1,1);
        changeMap.put(-1,2);
        changeMap.put(3,3);
        changeMap.put(-3,4);
        changeMap.put(5,5);
        changeMap.put(-5,6);
        changeMap.put(10,7);
        changeMap.put(-10,8);


        firstStateSpreadProb = new HashMap<Integer, Double>();
        firstStateSpreadProb.put(1, 0.33);
        firstStateSpreadProb.put(3, 0.33);
        firstStateSpreadProb.put(5, 0.34);
        secondStateSpreadProb = new HashMap<Integer, Double>();
        secondStateSpreadProb.put(1, 0.33);
        secondStateSpreadProb.put(3, 0.33);
        secondStateSpreadProb.put(5, 0.34);

        spreadMap = new HashMap<Integer, Integer>();
        spreadMap.put(1, 0);
        spreadMap.put(3, 1);
        spreadMap.put(5, 2);


        priceChangeAndSpread = new Integer[MaxLine][2];

    }



    private void getDataFromFile(){
        data = new double[MaxLine][2];

        try{
            File file = new File("case3data.csv");
            LineIterator it = FileUtils.lineIterator(file, "UTF-8");
            try {
                int lineCount = 0 ;
                it.nextLine();
                double[] newBidAsk = new double[2];
                while (it.hasNext()) {
                    String line = it.nextLine();
                    String[] BidAsk = line.split(",");
                    newBidAsk[0] = Double.parseDouble(BidAsk[0]);
                    newBidAsk[1] = Double.parseDouble(BidAsk[1]);
                    data[lineCount++][0] = newBidAsk[0];
                    data[lineCount-1][1] = newBidAsk[1];
                }
            } finally {
                LineIterator.closeQuietly(it);
            }
        }
        catch (IOException e){
            e.printStackTrace();
            System.out.println("Unable to open file");
        }

    }

    private double[][] getData(){
        return data;
    }

    public void updateParameter( Integer change, Integer spread){
        priceChangeAndSpread[time][0] = change;
        priceChangeAndSpread[time][1] = spread;

        // Use Message passing algorithm to estimate Hidden Markov Model
        // It is also called forward-backward algorithm or Baum-Welch algorithm(Both early founders of Renaissance)
        if(time >= 100){
            //Forward probability
            double[][] alpha  = new double[time+1][2];

            alpha[time -100][0] = firstStateEmisProb.get( priceChangeAndSpread[time -100][0] )* firstStateSpreadProb.get( priceChangeAndSpread[time- 100][1]) * stateProb[0];
            alpha[time -100][1] = secondStateEmisProb.get( priceChangeAndSpread[time -100][0]) * secondStateSpreadProb.get(priceChangeAndSpread[time -100][1]) * stateProb[1];
            System.out.println(time);

            for( int i = time -99; i <= time ; i++){
                alpha[i][0] = (alpha[i-1][0]*stateTransProb[0][0]+ alpha[i-1][1]*stateTransProb[1][0])* firstStateEmisProb.get( priceChangeAndSpread[i][0])
                        *firstStateSpreadProb.get(priceChangeAndSpread[i][1]);
                //System.out.println(alpha[i][0]);
                alpha[i][1] = (alpha[i-1][0]*stateTransProb[0][1]+ alpha[i-1][1]*stateTransProb[1][1])* secondStateEmisProb.get( priceChangeAndSpread[i][0])
                        *secondStateSpreadProb.get(priceChangeAndSpread[i][1]);
            }

            //backward probability
            double[][] beta = new double[time+1][2];
            beta[time][0] = firstStateEmisProb.get(priceChangeAndSpread[time][0]) * firstStateSpreadProb.get(priceChangeAndSpread[time][1]);
            beta[time][1] = secondStateEmisProb.get(priceChangeAndSpread[time][0]) * secondStateSpreadProb.get(priceChangeAndSpread[time][1]);

            for( int i= time -1; i>= time -100; i--){
                beta[i][0] = stateTransProb[0][0]*beta[i+1][0] *firstStateEmisProb.get(priceChangeAndSpread[i][0]) * firstStateSpreadProb.get(priceChangeAndSpread[i][1])
                        +stateTransProb[0][1]*beta[i+1][1] *secondStateEmisProb.get(priceChangeAndSpread[i][0]) * secondStateSpreadProb.get(priceChangeAndSpread[i][1]);
                beta[i][1] = stateTransProb[1][0]*beta[i+1][0] *firstStateEmisProb.get(priceChangeAndSpread[i][0]) * firstStateSpreadProb.get(priceChangeAndSpread[i][1])
                        +stateTransProb[1][1]*beta[i+1][1] *secondStateEmisProb.get(priceChangeAndSpread[i][0]) * secondStateSpreadProb.get(priceChangeAndSpread[i][1]);

            }

            // Update emission probability
            double[][] upDateProb = new double[time+1][2];
            double normalizer = alpha[time][0] + alpha[time][1];
            System.out.println( normalizer );
            for( int i= time -100 ; i<= time; i++ ){
                upDateProb[i][0] = alpha[i][0] * beta[i][0]/normalizer;
                upDateProb[i][1] = alpha[i][1] * beta[i][1]/normalizer;
            }

            double[][] updateEmisProb = new double[9][2];
            double[][] updateSpreadProb = new double[3][2];

            double[] sum = new double[2] ;

            for( int i= time-100 ; i<= time ; i++){
                updateEmisProb[  changeMap.get(priceChangeAndSpread[i][0]) ][0] += upDateProb[i][0];
                updateEmisProb[  changeMap.get(priceChangeAndSpread[i][0]) ][1] += upDateProb[i][1];
                updateSpreadProb[  spreadMap.get(priceChangeAndSpread[i][1]) ][0] += upDateProb[i][0];
                updateSpreadProb[  spreadMap.get(priceChangeAndSpread[i][1]) ][1] += upDateProb[i][1];
                sum[0] += upDateProb[i][0];
                sum[1] += upDateProb[i][1];
            }


            for( int i = 0; i< 9 ; i++ ){
                updateEmisProb[i][0] = updateEmisProb[i][0]/sum[0];
                firstStateEmisProb.put( changeState[i],updateEmisProb[i][0] );
                updateEmisProb[i][1] = updateEmisProb[i][1]/sum[1];
                secondStateEmisProb.put( changeState[i], updateEmisProb[i][1]);
            }
            //for( int i = 0; i< 9 ; i++ ){
            //    System.out.println( updateEmisProb[i][0] );
            //}

            for( int i = 0; i< 3;i++){
                updateSpreadProb[i][0]  = updateSpreadProb[i][0]/sum[0];
                firstStateSpreadProb.put( changeState[i],updateSpreadProb[i][0]);
                updateSpreadProb[i][1]  = updateSpreadProb[i][1]/sum[1];
                secondStateSpreadProb.put( changeState[i], updateSpreadProb[i][1]);
            }

            //for( int i = 0; i< 3 ; i++ ){
            //    System.out.println( updateSpreadProb[i][0] );
            //}

            //Update transition probability
            double[][] newTransProb = new double[2][2];
            for( int i = time-100 ; i< time; i++ ){
                newTransProb[0][0] += stateTransProb[0][0] * alpha[i][0]* beta[i+1][0] * firstStateEmisProb.get( priceChangeAndSpread[i][0])
                        *firstStateSpreadProb.get(priceChangeAndSpread[i][1]);
                newTransProb[0][1] += stateTransProb[0][1] * alpha[i][0]* beta[i+1][1] * secondStateEmisProb.get( priceChangeAndSpread[i][0])
                        *secondStateSpreadProb.get(priceChangeAndSpread[i][1]);
                newTransProb[1][0] += stateTransProb[1][0] * alpha[i][1]* beta[i+1][0] * firstStateEmisProb.get( priceChangeAndSpread[i][0])
                        *firstStateSpreadProb.get(priceChangeAndSpread[i][1]);
                newTransProb[1][1] += stateTransProb[1][1] * alpha[i][1]* beta[i+1][1] * secondStateEmisProb.get( priceChangeAndSpread[i][0])
                        *secondStateSpreadProb.get(priceChangeAndSpread[i][1]);
            }
            //System.out.println( newTransProb[0][1] );
            //System.out.println("sum"+ sum[0]);

            newTransProb[0][0] = (newTransProb[0][0] + stateTransProb[0][0] * alpha[time][0] * firstStateEmisProb.get( priceChangeAndSpread[time][0])
                    *firstStateSpreadProb.get(priceChangeAndSpread[time][1]) )/sum[0]/normalizer;
            newTransProb[0][1] = (newTransProb[0][1] + stateTransProb[0][1] * alpha[time][0] * secondStateEmisProb.get( priceChangeAndSpread[time][0])
                    *secondStateSpreadProb.get(priceChangeAndSpread[time][1]))/sum[0]/normalizer ;
            newTransProb[1][0] = (newTransProb[1][0] + stateTransProb[1][0] * alpha[time][1] * firstStateEmisProb.get( priceChangeAndSpread[time][0])
                    *firstStateSpreadProb.get(priceChangeAndSpread[time][1]))/sum[1]/normalizer;
            newTransProb[1][1] = (newTransProb[1][1] + stateTransProb[1][1] * alpha[time][1] * secondStateEmisProb.get( priceChangeAndSpread[time][0])
                    *secondStateSpreadProb.get(priceChangeAndSpread[time][1]))/sum[1]/normalizer;

            stateTransProb = newTransProb;
            System.out.println( newTransProb[0][1] );
        }





    }

    public void OrderFill(int aVolume, double aFillPrice){
        share += aVolume;
        //System.out.println("Share" + share + "Volume" + aVolume);
        cash  -= aVolume * aFillPrice;
    }

    private int getCurrentState(){
        double[] newStateProb = new double[2];
        //System.out.print(spread);
        newStateProb[0] = firstStateEmisProb.get(change ) * firstStateSpreadProb.get(spread)*  ( stateTransProb[0][0] * stateProb[0] + stateTransProb[1][0] * stateProb[1] );
        newStateProb[1] = secondStateEmisProb.get(change )* secondStateSpreadProb.get(spread)* ( stateTransProb[0][1] * stateProb[0] + stateTransProb[1][1] * stateProb[1] );
        stateProb[0] = newStateProb[0]/(newStateProb[0] + newStateProb[1]);
        stateProb[1] = newStateProb[1]/(newStateProb[0] + newStateProb[1]);
        return ( (stateProb[0 ] > stateProb[1])? 0 : 1);

    }

    public int getVolume( int state ){
        int volume = 0;

        double upProb = 0;
        double downProb = 0;
        if( state == 0 )
            for(int i = 0; i<changeState.length ; i++ )
                for(int j = 0; j< spreadState.length; j++ ){
                    if( changeState[i] > spreadState[j] + spread + 1e-14 ){
                        upProb +=  firstStateEmisProb.get(changeState[i]) * firstStateSpreadProb.get(spreadState[j]);
                    }
                }
        else
            for(int i = 0; i<changeState.length ; i++ )
                for(int j = 0; j< spreadState.length; j++ ){
                    if( - changeState[i] < spreadState[j] + spread - 1e-14 ){
                        downProb +=  secondStateEmisProb.get(changeState[i]) * secondStateEmisProb.get(spreadState[j]);
                    }
                }
        if( upProb > downProb ){
            if( share < 5 ){
                if( share >= 0 )
                    volume = 1;
                //else
                //    volume = -share + 1;
            }
        }
        else{
            if( share > -5 ){
                if( share <= 0)
                    volume = -1;
                //else
                //    volume = -share - 1;
            }

        }

        return volume;
    }

    public int newBidAsk(double aNewBid, double aNewAsk){
        double newPrice = (aNewBid + aNewAsk)/2;
        double newSpread = -(aNewBid - aNewAsk)/2;
        double newChange = newPrice - price;
        price = newPrice;
        spread = new Double(newSpread).intValue();
        change = new Double(newChange).intValue();

        if( time >= 0 && time <= 100){
            updateParameter(change, spread);
            time ++;
            return 0;
        }
        else{
            int volume = 0;
            int state = getCurrentState();
            volume = getVolume( state );

            // update parameter if latency permitted
            //updateParameter(change, spread);
            time ++;

            return volume;
        }



    }

    public void getPnl(){
        System.out.println(cash);
        System.out.println(share);
        System.out.println(" The PNL is " + (cash+ share * price));
    }

    public static void main(String arg[]){
        HMM model = new HMM();
        // Get data from file
        model.getDataFromFile();
        double[][] data = model.getData();
        // main part of the code

        int aVolume = 0;


        for(int i = 0; i< data.length ;i++){
            // Grabbing data;
            double[] newBidAsk =  data[i];
            double aNewBid = newBidAsk[0];
            double aNewAsk = newBidAsk[1];

            aVolume = model.newBidAsk( aNewBid, aNewAsk);
            double aFillPrice = (aVolume < 0)? aNewBid : aNewAsk;
            model.OrderFill( aVolume, aFillPrice);
        }

        model.getPnl();


    }
}
