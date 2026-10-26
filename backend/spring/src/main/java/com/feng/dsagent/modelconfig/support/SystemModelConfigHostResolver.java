package com.feng.dsagent.modelconfig.support;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public final class SystemModelConfigHostResolver implements ModelConfigHostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
