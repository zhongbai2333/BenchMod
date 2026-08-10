package com.zhongbai233.bench.network;

/** Layer at which a network impairment is actually applied and observed. */
public enum NetworkSemanticLayer {
    /** Ordered application byte stream between two TCP endpoints. */
    TCP_STREAM,
    /** Decoded Minecraft or Mod payload messages. */
    APPLICATION_MESSAGE,
    /** IP packets or TCP segments, requiring a privileged native backend. */
    IP_PACKET
}