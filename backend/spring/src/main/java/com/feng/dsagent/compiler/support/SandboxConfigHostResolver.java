package com.feng.dsagent.compiler.support;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface SandboxConfigHostResolver {

    public InetAddress[] resolve(String host) throws UnknownHostException;
}
