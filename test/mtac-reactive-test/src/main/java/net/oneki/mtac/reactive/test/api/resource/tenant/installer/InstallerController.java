package net.oneki.mtac.reactive.test.api.resource.tenant.installer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import net.oneki.mtac.api.ResourceController;

@RestController
public class InstallerController extends ResourceController<InstallerUpsertRequest, Installer, InstallerService> {
    @Autowired protected InstallerService installerService;

    @Override
    protected InstallerService getService() {
        return installerService;
    }

    @Override
    protected Class<InstallerUpsertRequest> getRequestClass() {
        return InstallerUpsertRequest.class;
    }

    @Override
    protected Class<Installer> getResourceClass() {
        return Installer.class;
    }
}
