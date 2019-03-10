package xeed.xposed.xkptgl;

final class Utils {
    static String getVal(int i) {
        if (i == 0) return "Auto";
        else if (i == 1) return "Semi";
        else if (i == 2) return "Manu";
        else return "None";
    }
}
