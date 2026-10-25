package com.feng.dsagent.modelconfig;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface ModelConfigHostResolver {

    public InetAddress[] resolve(String host) throws UnknownHostException;
}
