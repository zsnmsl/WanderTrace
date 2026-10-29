package com.wandertrace;

final class TravelProfiles {
    static final String[] IDS={"walking","jogging","running","cycling","driving","custom"};
    static final double[] SPEEDS={1.4,2.2,3.0,5.5,13.9,0};
    static int index(String id) {for(int i=0;i<IDS.length;i++) if(IDS[i].equals(id)) return i;return IDS.length-1;}
    static boolean usesSteps(int index) {return index>=0 && index<=2;}
    private TravelProfiles() {}
}
