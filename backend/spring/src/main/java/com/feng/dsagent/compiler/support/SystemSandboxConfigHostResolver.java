package com.feng.dsagent.compiler.support;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public final class SystemSandboxConfigHostResolver implements SandboxConfigHostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
