package com.wandertrace;

import java.util.Locale;

/** Presentation only: speed remains in m/s in the editor and km/h in the protocol. */
final class Pace {
    private Pace() {}
    static String format(double speedMps) {
        if (!Double.isFinite(speedMps) || speedMps<=0) return "—";
        long seconds=Math.round(1000.0/speedMps);
        return String.format(Locale.ROOT,"%d′%02d″",seconds/60,seconds%60);
    }
    static String summary(double speedMps,double randomPercent) {
        if (!Double.isFinite(speedMps) || speedMps<0) return "配速：请输入有效速度";
        if (speedMps==0) return "配速：—（静止）";
        String base="配速："+format(speedMps)+" /公里";
        if (!Double.isFinite(randomPercent) || randomPercent<0 || randomPercent>50) return base;
        if (randomPercent==0) return base+"\n当前为匀速；增加速度随机幅度后，各公里配速会随运行变化。";
        double amplitude=randomPercent/100.0;
        return base+"\n随机段参考："+format(speedMps*(1+amplitude))+"–"+format(speedMps*(1-amplitude))+" /公里";
    }
}
