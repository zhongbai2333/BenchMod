package com.zhongbai233.bench.gradle;

/** Stable names for isolated participants in a paired benchmark run. */
final class PairedParticipantLayout {
    private PairedParticipantLayout() {}

    static String remoteClientRunType(String index, String count) {
        return remoteClientRunType(Integer.parseInt(index), Integer.parseInt(count));
    }

    static String remoteClientRunType(int index, int count) {
        if (count < 1 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Remote client index " + index
                    + " is outside client count " + count);
        }
        return count == 1 ? "remote-client" : "remote-client-" + index;
    }
}
