package prizm;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hello world!
 *
 */
public class TestFactory {

    public static ParaBlock.Transaction getTrx(long txID, int stamp, long from, long to, long amount, long fee) {
        ParaBlock.Transaction trx = new ParaBlock.Transaction();
        trx.setAmount(amount);
        trx.setID(txID);
        trx.setFee(fee);
        trx.setReceiver(to);
        trx.setSender(from);
        trx.setStamp(stamp);
        trx.setType(ParaBlock.Type.ORDINARY);
        return trx;
    }

    public static ParaBlock.Transaction getTrxPara(long txID, int stamp, long from, long to, long amount, long fee, Long paraBlockID, Long paraTxID) {
        ParaBlock.Transaction trx = new ParaBlock.Transaction();
        trx.setAmount(amount);
        trx.setID(txID);
        trx.setFee(fee);
        trx.setReceiver(to);
        trx.setSender(from);
        trx.setStamp(stamp);
        trx.setType(ParaBlock.Type.PARAMINING);
        trx.setParaBlockID(paraBlockID);
        trx.setParaTxID(paraTxID);
        return trx;
    }

//        Random rnd = new Random();
//        ArrayList<ParaBlock.Transaction> trx = new ArrayList<>();
//        for (int i = 0; i < 100; i++) {
//            ParaBlock.Transaction item  = new ParaBlock.Transaction();
//            item.setStamp(i+10000);
//            item.setID(rnd.nextLong());
//            trx.add(item);
//            
//            item  = new ParaBlock.Transaction();
//            item.setStamp(i+10002);
//            item.setID(rnd.nextLong());
//            trx.add(item);
//
//            item  = new ParaBlock.Transaction();
//            item.setStamp(i+9996);
//            item.setID(rnd.nextLong());
//            trx.add(item);
//
//            item  = new ParaBlock.Transaction();
//            item.setStamp(i+9996);
//            item.setID(rnd.nextLong());
//            trx.add(item);
//
//            item  = new ParaBlock.Transaction();
//            item.setStamp(i+9995);
//            item.setID(rnd.nextLong());
//            trx.add(item);
//        }
//        List<ParaBlock.Transaction> result = ParaBlock.sort(trx);
//        for (ParaBlock.Transaction item : result) {
//            System.out.println(">"+item.toString());
//        }
//        ParaMetrics metrics = new ParaMetrics();
//        metrics.setBeforeStamp(42);
//        metrics.setAfterStamp(201400);
//        metrics.setAmount(100000);
//        metrics.setBalance(141212);
//        metrics.calculate();
//        System.out.println("ParaMetrics: "+metrics.toString());
}
