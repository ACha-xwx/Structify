package com.feng.dsagent.auth.worker;

import com.feng.dsagent.auth.config.BootstrapAdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.feng.dsagent.auth.config.BootstrapAdminProperties;
import com.feng.dsagent.auth.support.AdminAccountProvisioner;
/** Runs only when BOOTSTRAP_ADMIN_PROVISION_ENABLED=true is set for a controlled deployment. */
@Component
public final class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminProperties properties;
    private final AdminAccountProvisioner provisioner;

    public BootstrapAdminRunner(BootstrapAdminProperties properties, AdminAccountProvisioner provisioner) {
        this.properties = properties;
        this.provisioner = provisioner;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.enabled()) {
            provisioner.provisionAdministrator(
                properties.email(),
                properties.username(),
                properties.password(),
                properties.reconcileExisting()
            );
            log.info(
                "Controlled bootstrap administrator provisioning completed; reconciliation={}",
                properties.reconcileExisting()
            );
        }
    }
}
