package com.feng.dsagent.compiler;

import java.net.InetAddress;
import java.net.UnknownHostException;

interface SandboxConfigHostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
